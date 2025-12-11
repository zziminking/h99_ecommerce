# 분산 트랜잭션 설계 문서

## 목차
1. [현황 분석](#1-현황-분석)
2. [도메인별 DB 분리 시나리오](#2-도메인별-db-분리-시나리오)
3. [발생 가능한 문제점 및 Saga 패턴 해결 방안](#3-발생-가능한-문제점-및-saga-패턴-해결-방안)
4. [분산 트랜잭션 해결 방안 비교](#4-분산-트랜잭션-해결-방안-비교)
5. [Saga Orchestration 패턴 설계](#5-saga-orchestration-패턴-설계)
6. [데이터 일관성 보장 메커니즘](#6-데이터-일관성-보장-메커니즘)
7. [결론 및 기대효과](#7-결론-및-기대효과)

---

## 1. 현황 분석

### 1.1 현재 아키텍처

**모놀리식 구조**
```
┌─────────────────────────────────────┐
│   E-Commerce Application            │
│  ┌────────────────────────────────┐ │
│  │      Service Layer             │ │
│  │  OrderService                  │ │
│  │  ProductService                │ │
│  │  PaymentService                │ │
│  │  CouponService                 │ │
│  │  CartItemService               │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │     Single Database            │ │
│  │  - Order                       │ │
│  │  - Product                     │ │
│  │  - User/Point                  │ │
│  │  - Coupon                      │ │
│  │  - CartItem                    │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 1.2 현재 트랜잭션 처리 방식

**주문 생성 프로세스 (OrderService.createOrder)**

```
[OrderService.createOrder() - @Transactional]
├─ 1. 장바구니 조회 (CartItem)
├─ 2. 사용자 조회 (User)
├─ 3. 재고 차감 (Product) ← @CustomTransactional (별도 트랜잭션)
├─ 4. 결제 처리 (User, Point, Coupon) ← @CustomTransactional (별도 트랜잭션)
├─ 5. 주문 저장 (Order)
├─ 6. 장바구니 비우기 (CartItem)
└─ 7. 이벤트 발행 (OrderCompletedEvent)
```

**참여 도메인**: Order, CartItem, Product, User, Point, Coupon (총 6개)

### 1.3 트랜잭션 분리 현황

| 메서드 | 트랜잭션 관리 | 분산 락 | 비고 |
|--------|-------------|---------|------|
| OrderService.createOrder() | @Transactional | ❌ | 전체 주문 프로세스 |
| ProductService.deductStock() | @CustomTransactional | ✅ Redis Lock | 재고 차감 |
| PaymentService.processPayment() | @CustomTransactional | ✅ Redis Lock | 포인트/쿠폰 처리 |
| CouponService.issueCoupon() | Redis Lua + 비동기 | ✅ | 선착순 쿠폰 |

**핵심 문제점**:
- ProductService와 PaymentService가 별도 트랜잭션으로 분리되어 있음
- OrderService 트랜잭션 실패 시 수동 보상 트랜잭션(restoreStock) 실행
- 트랜잭션 경계 불일치로 인한 데이터 불일치 가능성

---

## 2. 도메인별 DB 분리 시나리오

### 2.1 MSA 전환 시 아키텍처 변화

```
현재 (Monolithic)                        MSA 전환 후
─────────────────                        ────────────────────────────────────
┌─────────────────┐                     ┌──────────┐ ┌──────────┐ ┌──────────┐
│  Application    │                     │  Order   │ │ Product  │ │ Payment  │
│                 │                     │  Service │ │ Service  │ │ Service  │
│  ┌───────────┐  │                     │          │ │          │ │          │
│  │ Single DB │  │  ══════════>        │ ┌──────┐ │ │ ┌──────┐ │ │ ┌──────┐ │
│  └───────────┘  │                     │ │Order │ │ │ │Product││ │ │Payment││
│                 │                     │ │  DB  │ │ │ │  DB  │ │ │ │  DB  │ │
└─────────────────┘                     │ └──────┘ │ │ └──────┘ │ │ └──────┘ │
                                        └──────────┘ └──────────┘ └──────────┘
```

### 2.2 서비스 분리 전략

| 서비스 | 담당 도메인 | 데이터베이스 |
|--------|-----------|------------|
| **Order Service** | Order, OrderItem, CartItem | order_db |
| **Product Service** | Product, Stock, ProductStatistics | product_db |
| **Payment Service** | User, Point, Coupon, UserCoupon | payment_db |

**핵심 변화**:
- 단일 트랜잭션으로 처리되던 주문 프로세스가 3개의 독립된 서비스로 분산
- 각 서비스는 자신의 DB만 접근 가능 (Database per Service 원칙)
- 서비스 간 통신은 REST API 또는 Message Queue 사용

---

## 3. 발생 가능한 문제점 및 Saga 패턴 해결 방안

### 3.1 트랜잭션 원자성(Atomicity) 문제

#### 🔴 문제 1: 부분 실패 (Partial Failure)

**발생 시나리오**:
```
주문 생성 프로세스:
1. Order Service: 주문 생성 ✅ (order_db 커밋)
2. Product Service: 재고 차감 요청 ✅ (HTTP 200, product_db 커밋)
3. Payment Service: 결제 처리 요청 ❌ (HTTP 500 - 포인트 부족)

최종 상태:
✅ Order: 주문이 생성됨 (PENDING 상태)
✅ Product: 재고가 차감됨
❌ Payment: 결제 실패

→ 재고는 차감되었으나 결제는 완료되지 않음
→ 데이터 불일치 발생!
```

**영향도**:
- 재고 부족 현상 발생
- 실제 판매되지 않은 상품의 재고 차감
- 고객 불만 및 비즈니스 손실

**Saga Orchestration 해결 방안**:

```
Saga Orchestrator가 전체 플로우를 관리:

1. Order 생성 (PENDING 상태)
2. Product Service 호출 → 재고 차감 성공
3. Payment Service 호출 → 결제 실패 감지 ❌

4. Orchestrator가 자동으로 보상 트랜잭션 실행:
   ├─ Product Service: 재고 복구 (Compensate)
   └─ Order Service: 주문 취소 (CANCELED 상태)

5. 최종 상태:
   ✅ Product: 재고 복구됨
   ✅ Order: CANCELED 상태
   ✅ 데이터 일관성 보장
```

**해결 메커니즘**:
- Orchestrator가 각 Step의 성공/실패를 추적
- 실패 지점 이후의 모든 완료된 Step에 대해 역순으로 보상 실행
- 보상 트랜잭션이 자동으로 실행되어 수동 개입 불필요

---

#### 🔴 문제 2: 네트워크 타임아웃

**발생 시나리오**:
```
주문 생성 프로세스:
1. Order Service: 주문 생성 ✅
2. Order Service → Product Service: 재고 차감 요청 전송
   - Product Service: 재고 차감 완료 ✅
   - 응답 전송 중 네트워크 타임아웃 발생
   - Order Service: 타임아웃으로 실패 인식 ❌
3. Order Service: 주문 롤백 시도

최종 상태:
❌ Order: 주문이 없음 (롤백됨)
✅ Product: 재고가 차감됨 (고아 트랜잭션)

→ 재고는 차감되었으나 주문은 없음
```

**영향도**:
- 재고 데이터 부정확
- 운영 부담 증가 (수동 복구)

**Saga Orchestration 해결 방안**:

```
Saga Log를 통한 상태 추적:

1. Orchestrator: Product Service 호출
2. Saga Log에 "STOCK_DEDUCT_REQUESTED" 기록
3. Timeout 발생
4. Orchestrator: Saga Log 확인
   - Product Service 호출 여부 확인
   - 재시도 또는 보상 결정

재시도 전략:
- Product Service에 멱등성 키를 함께 전송
- Product Service는 이미 처리된 요청인지 확인
- 중복 처리 방지

최종 상태:
✅ Product: 재고 상태 정확
✅ Order: 재시도 성공 또는 보상 완료
✅ 고아 트랜잭션 방지
```

**해결 메커니즘**:
- Saga Log를 통한 정확한 상태 추적
- 멱등성 키를 통한 중복 요청 방지
- 재시도 정책 (지수 백오프)
- Timeout 발생 시 보상 트랜잭션 실행

---

#### 🔴 문제 3: 분산 락 경합 및 데드락

**발생 시나리오 1: 분산 락이 없는 경우**
```
마지막 1개 남은 상품에 대해 동시 주문:

시간 T1: 사용자 A → Product Service (재고 확인: 1개 남음)
시간 T1: 사용자 B → Product Service (재고 확인: 1개 남음)
시간 T2: A → 재고 차감 (1 → 0)
시간 T2: B → 재고 차감 (1 → 0) // 동시 실행
시간 T3: 재고 = -1 ❌

→ 오버셀링 (Overselling) 발생
```

**발생 시나리오 2: 데드락**
```
두 주문이 서로 다른 순서로 락 획득:

주문 A: Product Lock 획득 → Payment Lock 대기
주문 B: Payment Lock 획득 → Product Lock 대기
→ 무한 대기 상태 (Deadlock)
```

**영향도**:
- 비즈니스 손실 (오버셀링)
- 고객 신뢰도 하락
- 시스템 응답 불가

**Saga Orchestration 해결 방안**:

```
Orchestrator의 중앙 집중식 락 관리:

오버셀링 방지:
1. Orchestrator가 Product Service 호출 전 분산 락 획득
2. Lock Key: "product:stock:{productId}"
3. 락 획득 성공 시에만 재고 차감 요청
4. 작업 완료 후 락 해제

데드락 방지:
1. Orchestrator가 일관된 순서로 서비스 호출
   - 항상: Product → Payment 순서 고정
2. 락 획득 순서 표준화:
   - 모든 주문이 동일한 순서로 락 획득
3. 락 타임아웃 설정:
   - 일정 시간 내 락 획득 실패 시 자동 해제
   - 데드락 자동 복구

최종 상태:
✅ 재고 정확성 보장 (오버셀링 방지)
✅ 데드락 발생 방지
✅ 안정적인 동시성 제어
```

**해결 메커니즘**:
- 중앙 Orchestrator의 락 관리
- 일관된 서비스 호출 순서
- 락 타임아웃 및 자동 복구
- 분산 락 (Redis) 활용

---

### 3.2 일관성(Consistency) 문제

#### 🟡 문제 4: 읽기 일관성

**발생 시나리오**:
```
Order Service가 Product Service에서 재고 조회 후 주문:

시간 T1: Order Service → Product Service: 재고 100개 조회
시간 T2: 다른 주문으로 재고 10개 차감 (90개 남음)
시간 T3: Order Service: 100개 기준으로 10개 주문 시도
시간 T4: Product Service: 재고 부족 오류 반환

→ Order Service가 보유한 재고 정보가 실시간이 아님
→ 사용자 경험 저하 (주문 실패)
```

**영향도**:
- 사용자 불편
- 재시도 로직 필요
- 주문 전환율 감소

**Saga Orchestration 해결 방안**:

```
Orchestrator의 실시간 재고 확인:

1. Orchestrator: Product Service에 재고 확인 + 예약 요청
   - 분산 락으로 보호된 재고 확인
   - 즉시 재고 예약 (Pessimistic Lock)

2. 재고 예약 성공:
   - 일정 시간(예: 5분) 동안 해당 재고 예약
   - 다른 주문은 예약된 재고 제외한 수량만 확인 가능

3. Payment 처리:
   - 성공: 재고 예약 → 확정
   - 실패: 재고 예약 → 해제

최종 상태:
✅ 실시간 재고 확인
✅ 재고 부족으로 인한 주문 실패 최소화
✅ 사용자 경험 개선
```

**해결 메커니즘**:
- 재고 예약 메커니즘 (Pessimistic Lock)
- 실시간 재고 확인
- 예약 타임아웃 관리
- 보상 시 예약 자동 해제

---

#### 🟡 문제 5: 이벤트 중복 처리

**발생 시나리오**:
```
OrderCompletedEvent 발행 및 처리:

1. Order Service: OrderCompletedEvent 발행
2. Message Queue: 이벤트 적재
3. Product Service: 이벤트 수신 및 통계 업데이트 시작
4. Product Service: 처리 중 DB 커넥션 에러 발생
5. Message Queue: 재시도 (최대 3회)
6. Product Service: 동일한 이벤트를 3번 처리
   → 통계가 3배로 증가 ❌

→ 통계 및 집계 데이터 부정확
```

**영향도**:
- 데이터 정합성 문제
- 의사결정 왜곡
- 통계 신뢰도 저하

**Saga Orchestration 해결 방안**:

```
멱등성(Idempotency) 보장:

1. 이벤트에 고유 ID 부여:
   - eventId = "{orderId}_{timestamp}_{sequenceNumber}"

2. ProcessedEvent 테이블:
   - 이벤트 처리 전 중복 검사
   - 이미 처리된 이벤트는 스킵

처리 로직:
┌─────────────────────────────────────┐
│ handleOrderCompleted(event):        │
│                                     │
│ 1. if (processed(event.id)):        │
│      return // 이미 처리됨          │
│                                     │
│ 2. BEGIN TRANSACTION                │
│    - 비즈니스 로직 실행             │
│    - ProcessedEvent 저장            │
│    COMMIT                           │
│                                     │
│ 3. 통계 업데이트 (정확히 1번만)    │
└─────────────────────────────────────┘

최종 상태:
✅ 이벤트 중복 처리 방지
✅ 통계 정확성 보장
✅ 데이터 정합성 유지
```

**해결 메커니즘**:
- 이벤트 고유 ID
- ProcessedEvent 테이블
- 트랜잭션 내 멱등성 키 저장
- 중복 검사 로직

---

### 3.3 격리성(Isolation) 문제

#### 🟡 문제 6: Dirty Read

**발생 시나리오**:
```
Payment Service에서 포인트 차감 중 다른 트랜잭션이 읽기:

트랜잭션 A (포인트 차감):
  1. User 포인트 조회: 10,000원
  2. 포인트 차감: 10,000 → 9,000원
  3. DB 업데이트 (미커밋)

트랜잭션 B (포인트 조회):
  1. User 포인트 조회: 9,000원 (커밋 전 데이터 읽음)
  2. 포인트 부족으로 주문 거부

트랜잭션 A:
  3. 롤백 발생 (포인트 다시 10,000원)

→ 트랜잭션 B는 잘못된 데이터로 의사결정
→ 실제로는 포인트가 충분했으나 주문 거부됨
```

**영향도**:
- 고객 불만
- 비즈니스 기회 손실
- 데이터 무결성 위협

**Saga Orchestration 해결 방안**:

```
Orchestrator의 순차적 처리 + 분산 락:

1. Orchestrator: Payment 요청 전 User Lock 획득
   - Lock Key: "user:payment:{userId}"

2. Payment Service 호출:
   - 포인트 조회, 차감, 저장
   - 트랜잭션 커밋

3. Lock 해제

다른 주문 시도:
- Lock 획득 시도
- Lock이 있으면 대기 (Timeout: 10초)
- Lock 해제 후 최신 포인트로 처리

최종 상태:
✅ Dirty Read 방지
✅ 포인트 정확성 보장
✅ 동시성 제어
```

**해결 메커니즘**:
- 사용자별 분산 락
- Orchestrator의 순차적 처리
- 커밋 후 락 해제
- READ_COMMITTED 격리 수준 + 락 조합

---

### 3.4 지속성(Durability) 문제

#### 🔴 문제 7: 서비스 장애 시 복구

**발생 시나리오**:
```
Order Service에서 주문 생성 프로세스 중 서버 크래시:

1. Order 생성 완료 (PENDING 상태)
2. Product Service에 재고 차감 요청 전송 ✅
3. Product Service: 재고 차감 완료 ✅
4. Product Service: 응답 전송
5. Order Service: 서버 크래시 💥 (응답 수신 전)

재시작 후 상태:
- Order: PENDING 상태로 남음
- Product: 재고 차감됨
- 재고 차감 여부 불명확

→ 주문 상태가 불명확
→ 수동 개입 필요
```

**영향도**:
- 운영 부담 증가
- 고객 불만
- 주문 처리 지연

**Saga Orchestration 해결 방안**:

```
Saga Log를 통한 자동 복구:

Saga Log (DB 저장):
┌─────────────────────────────────────────┐
│ sagaId: "saga-12345"                    │
│ orderId: 12345                          │
│ status: "STOCK_DEDUCTED"                │
│ steps:                                  │
│   - ORDER_CREATED: SUCCESS              │
│   - STOCK_DEDUCTED: SUCCESS             │
│   - PAYMENT_COMPLETED: PENDING ← 여기서 중단 │
└─────────────────────────────────────────┘

서버 재시작 시:
1. Orchestrator: 미완료 Saga 조회
   - status가 COMPLETED 또는 FAILED가 아닌 Saga 검색

2. 마지막 Step 확인:
   - STOCK_DEDUCTED까지 완료됨

3. 자동 복구:
   - PAYMENT_COMPLETED Step 재시도
   - 성공: Saga 완료
   - 실패: 보상 트랜잭션 실행 (재고 복구)

최종 상태:
✅ 자동 복구
✅ 수동 개입 불필요
✅ 데이터 일관성 보장
```

**해결 메커니즘**:
- Saga Log 영속화 (DB)
- 서버 재시작 시 미완료 Saga 자동 감지
- 마지막 Step부터 재시작
- 보상 트랜잭션 자동 실행

---

### 3.5 성능(Performance) 문제

#### 🟡 문제 8: 분산 트랜잭션 오버헤드

**발생 시나리오**:
```
모놀리식 (현재):
├─ 단일 트랜잭션: ~100ms
├─ 네트워크 호출: 0회
└─ 총 응답 시간: ~100ms

MSA (분산 환경):
├─ Order Service 로직: ~30ms
├─ Order → Product (네트워크): ~50ms
├─ Product Service 로직: ~30ms
├─ Order → Payment (네트워크): ~50ms
├─ Payment Service 로직: ~40ms
├─ 분산 트랜잭션 코디네이터: ~100ms
└─ 총 응답 시간: ~300ms

성능 저하: 3배 증가
```

**영향도**:
- 사용자 경험 저하
- 시스템 처리량(Throughput) 감소
- 인프라 비용 증가

**Saga Orchestration 해결 방안**:

```
성능 최적화 전략:

1. 비동기 처리:
   ┌─────────────────────────────────────┐
   │ 동기 (Critical Path):               │
   │ - Order 생성                        │
   │ - 재고 차감                         │
   │ - 결제 처리                         │
   │ → 사용자 응답 (200ms)               │
   │                                     │
   │ 비동기 (Non-Critical Path):         │
   │ - 통계 업데이트                     │
   │ - 알림 발송                         │
   │ - 포인트 적립                       │
   │ → 백그라운드 처리                   │
   └─────────────────────────────────────┘

2. 캐싱:
   - Product 정보 캐싱 (Redis)
   - User 포인트 캐싱
   - 재고는 실시간 조회 (정확성 우선)

3. 병렬 처리:
   - 독립적인 Step은 병렬 실행
   - 예: 재고 확인 + 쿠폰 검증 동시 실행

4. Connection Pooling:
   - HTTP Client Connection Pool 최적화
   - DB Connection Pool 증설

최종 성능:
✅ 응답 시간: 300ms → 150ms (50% 개선)
✅ 처리량: 100 TPS → 300 TPS
✅ 사용자 경험 개선
```

**해결 메커니즘**:
- Critical Path와 Non-Critical Path 분리
- 비동기 처리 (TransactionalEventListener)
- 캐싱 전략
- 병렬 처리
- 인프라 최적화

---

### 3.6 운영(Operational) 문제

#### 🟡 문제 9: 디버깅 어려움

**발생 시나리오**:
```
주문 실패 원인 파악:

Order Service 로그:
[2025-12-11 20:00:01] 주문 생성 시작 - orderId: 12345
[2025-12-11 20:00:05] 주문 생성 실패

Product Service 로그:
[2025-12-11 20:00:02] 재고 차감 성공 - productId: 789, quantity: 2

Payment Service 로그:
[2025-12-11 20:00:04] 결제 실패 - 포인트 부족 - userId: 456

→ 로그가 서비스별로 분산되어 있음
→ 타임스탬프와 컨텍스트로 추적해야 함
→ 실패 지점과 원인 파악 어려움
```

**영향도**:
- 장애 대응 시간 증가
- 평균 복구 시간(MTTR) 증가
- 운영 효율성 저하

**Saga Orchestration 해결 방안**:

```
Saga Log 기반 통합 추적:

Saga Log 기록:
┌──────────────────────────────────────────────┐
│ sagaId: saga-12345                           │
│ orderId: 12345                               │
│ status: FAILED                               │
│                                              │
│ steps:                                       │
│ 1. ORDER_CREATED                             │
│    - timestamp: 2025-12-11 20:00:01          │
│    - service: OrderService                   │
│    - status: SUCCESS                         │
│    - data: { orderId: 12345, amount: 50000 }│
│                                              │
│ 2. STOCK_DEDUCTED                            │
│    - timestamp: 2025-12-11 20:00:02          │
│    - service: ProductService                 │
│    - status: SUCCESS                         │
│    - data: { productId: 789, quantity: 2 }   │
│                                              │
│ 3. PAYMENT_COMPLETED                         │
│    - timestamp: 2025-12-11 20:00:04          │
│    - service: PaymentService                 │
│    - status: FAILED ❌                       │
│    - error: "포인트 부족 - 현재: 3000원"     │
│                                              │
│ 4. COMPENSATING                              │
│    - STOCK_RESTORED: SUCCESS                 │
│    - ORDER_CANCELED: SUCCESS                 │
└──────────────────────────────────────────────┘

장점:
✅ 전체 플로우를 하나의 Saga Log에서 확인
✅ 실패 지점 명확히 파악
✅ 보상 트랜잭션 실행 여부 확인
✅ 장애 대응 시간 단축

추가: 분산 추적 (Distributed Tracing)
- Zipkin/Jaeger 연동
- Trace ID로 모든 서비스 로그 연결
- 시각화된 플로우 차트
```

**해결 메커니즘**:
- Saga Log 중앙 집중 관리
- Step별 상세 정보 기록
- 실패 원인 및 보상 내역 추적
- 분산 추적 시스템 연동
- 통합 모니터링 대시보드

---

## 4. 분산 트랜잭션 해결 방안 비교

### 4.1 주요 해결 방안

#### Saga Pattern - Choreography (이벤트 기반)
- 각 서비스가 로컬 트랜잭션 실행 후 이벤트 발행
- 다음 서비스가 이벤트를 구독하여 처리
- 탈중앙화된 구조

**장점**: 서비스 간 느슨한 결합, 독립적 확장
**단점**: 전체 플로우 파악 어려움, 순환 의존성 발생 가능

#### Saga Pattern - Orchestration (중앙 조율) ⭐
- 중앙 Orchestrator가 Saga 실행 관리
- 명시적으로 각 서비스 호출 및 순서 제어
- 보상 트랜잭션 중앙 관리

**장점**: 전체 플로우 명확, 에러 처리 집중화, 테스트/디버깅 용이
**단점**: Orchestrator가 단일 장애점(SPOF), 서비스 간 결합도 증가

#### Outbox Pattern
- 이벤트를 DB에 먼저 저장 후 별도 프로세스가 발행
- 트랜잭션과 이벤트 발행의 원자성 보장
- 이벤트 유실 방지

**장점**: 이벤트 발행 보장, 재시도 메커니즘
**단점**: Outbox 테이블 관리 필요, 폴링 시 지연 발생

#### 2PC (Two-Phase Commit)
- 준비(Prepare) → 커밋(Commit) 2단계 프로토콜
- 모든 참여자가 동의해야 커밋
- 강한 일관성 보장

**장점**: ACID 완전 보장, 비즈니스 로직 단순
**단점**: 성능 저하(Blocking), 확장성 제한, 단일 장애점

### 4.2 이커머스 도메인 특성 고려

| 요구사항 | 중요도 | 적합한 방식 |
|---------|--------|-----------|
| 재고 정확성 | 🔴 Critical | 분산 락 + Saga Orchestration |
| 결제 정확성 | 🔴 Critical | Saga (보상 트랜잭션) |
| 응답 속도 | 🟡 High | Saga (비동기 처리) |
| 주문 처리량 | 🟡 High | Saga (확장성) |
| 통계 정확성 | 🟢 Medium | 최종 일관성 허용 |
| 디버깅 용이성 | 🟡 High | Orchestration |

### 4.3 최종 선택: Saga Orchestration + Outbox Pattern

**선택 이유**:
1. ✅ 전체 주문 플로우를 중앙에서 관리 (가시성)
2. ✅ 보상 트랜잭션 로직 집중화 (유지보수)
3. ✅ 이벤트 유실 방지 (Outbox)
4. ✅ 이커머스 특성상 최종 일관성 허용 가능
5. ✅ 운영 및 모니터링 용이
6. ✅ 위에서 분석한 9가지 문제점 모두 해결 가능

---

## 5. Saga Orchestration 패턴 설계

### 5.1 Saga Orchestrator 아키텍처

```
                    ┌────────────────────────────┐
                    │   API Gateway              │
                    └────────────┬───────────────┘
                                 │
                                 │ HTTP POST /orders
                                 │
              ┌──────────────────▼──────────────────┐
              │  Order Saga Orchestrator            │
              │                                     │
              │  ┌───────────────────────────────┐  │
              │  │   Saga State Machine          │  │
              │  │                               │  │
              │  │   PENDING                     │  │
              │  │      ↓                        │  │
              │  │   ORDER_CREATED               │  │
              │  │      ↓                        │  │
              │  │   STOCK_DEDUCTED              │  │
              │  │      ↓                        │  │
              │  │   PAYMENT_COMPLETED           │  │
              │  │      ↓                        │  │
              │  │   ORDER_COMPLETED             │  │
              │  │                               │  │
              │  │   (실패 시)                   │  │
              │  │      ↓                        │  │
              │  │   COMPENSATING                │  │
              │  │      ↓                        │  │
              │  │   FAILED/CANCELED             │  │
              │  └───────────────────────────────┘  │
              │                                     │
              │  ┌───────────────────────────────┐  │
              │  │   Compensation Handler        │  │
              │  │   - Restore Stock             │  │
              │  │   - Refund Payment            │  │
              │  │   - Cancel Order              │  │
              │  └───────────────────────────────┘  │
              └──────┬──────────┬─────────┬─────────┘
                     │          │         │
        ┌────────────┼──────────┼─────────┼──────────┐
        │            │          │         │          │
        │ REST API   │          │         │          │
        │            │          │         │          │
   ┌────▼─────┐ ┌───▼────┐ ┌───▼─────┐ ┌▼────────┐ │
   │  Order   │ │Product │ │ Payment │ │  Saga   │ │
   │  Service │ │Service │ │ Service │ │   Log   │ │
   │          │ │        │ │         │ │   DB    │ │
   │ ┌──────┐ │ │┌──────┐│ │┌───────┐│ │┌───────┐│ │
   │ │Order │ │ ││Product││ ││Payment││ ││ Saga  ││ │
   │ │  DB  │ │ ││  DB  ││ ││  DB  ││ ││ Log   ││ │
   │ │Outbox│ │ ││Outbox││ ││Outbox ││ │└───────┘│ │
   │ └──────┘ │ │└──────┘│ │└───────┘│ │         │ │
   └──────────┘ └────────┘ └─────────┘ └─────────┘ │
                                                     │
   ┌─────────────────────────────────────────────────┘
   │
   │  Message Queue (Kafka)
   │  ┌─────────────────────────────────────┐
   └─>│  - OrderCreatedEvent                │
      │  - StockDeductedEvent               │
      │  - PaymentCompletedEvent            │
      │  - CompensationEvent                │
      └─────────────────────────────────────┘
```

### 5.2 Saga 실행 플로우 (성공 시나리오)

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Orchestrator│  │   Order     │  │  Product    │  │  Payment    │
│             │  │   Service   │  │  Service    │  │  Service    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │                │
       │ 1. Start Saga  │                │                │
       │────────────────>│                │                │
       │                │                │                │
       │ 2. Create Order│                │                │
       │    (PENDING)   │                │                │
       │<───────────────│                │                │
       │                │                │                │
       │ 3. Deduct Stock Request         │                │
       │─────────────────────────────────>│                │
       │                │                │                │
       │                │ 4. Deduct Stock│                │
       │                │    (Success)   │                │
       │<─────────────────────────────────│                │
       │                │                │                │
       │ 5. Process Payment Request      │                │
       │────────────────────────────────────────────────>│
       │                │                │                │
       │                │                │ 6. Process     │
       │                │                │    Payment     │
       │                │                │    (Success)   │
       │<────────────────────────────────────────────────│
       │                │                │                │
       │ 7. Complete Order               │                │
       │────────────────>│                │                │
       │                │                │                │
       │ 8. Order Status│                │                │
       │    COMPLETED   │                │                │
       │<───────────────│                │                │
       │                │                │                │
       │ 9. Saga        │                │                │
       │    COMPLETED   │                │                │
       │                │                │                │
```

### 5.3 Saga 보상 플로우 (실패 시나리오)

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Orchestrator│  │   Order     │  │  Product    │  │  Payment    │
│             │  │   Service   │  │  Service    │  │  Service    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │                │
       │ 1-4. (위와 동일)                 │                │
       │                │                │                │
       │ 5. Process Payment Request      │                │
       │────────────────────────────────────────────────>│
       │                │                │                │
       │                │                │ 6. Process     │
       │                │                │    Payment     │
       │                │                │    FAILED ❌   │
       │<────────────────────────────────────────────────│
       │                │                │                │
       │ 7. Start       │                │                │
       │    Compensation│                │                │
       │                │                │                │
       │ 8. Restore Stock                │                │
       │─────────────────────────────────>│                │
       │                │                │                │
       │                │ 9. Restore     │                │
       │                │    (Success)   │                │
       │<─────────────────────────────────│                │
       │                │                │                │
       │ 10. Cancel Order                │                │
       │────────────────>│                │                │
       │                │                │                │
       │ 11. Order Status                │                │
       │     CANCELED   │                │                │
       │<───────────────│                │                │
       │                │                │                │
       │ 12. Saga       │                │                │
       │     FAILED     │                │                │
       │                │                │                │
```

### 5.4 Saga State Machine

```
                    ┌──────────────┐
                    │   PENDING    │
                    └──────┬───────┘
                           │
                           │ Start Saga
                           ▼
                    ┌──────────────┐
                    │ORDER_CREATED │
                    └──────┬───────┘
                           │
                           │ Deduct Stock Success
                           ▼
                    ┌──────────────┐
              ┌─────│STOCK_DEDUCTED│
              │     └──────┬───────┘
              │            │
              │            │ Process Payment Success
              │            ▼
              │     ┌──────────────┐
              │     │PAYMENT_      │
              │     │COMPLETED     │
              │     └──────┬───────┘
              │            │
              │            │ Complete Order
              │            ▼
              │     ┌──────────────┐
              │     │ORDER_        │
              │     │COMPLETED     │
              │     └──────────────┘
              │            │
              │            │ Saga Success
              │            ▼
              │     ┌──────────────┐
              │     │  SUCCESS ✅  │
              │     └──────────────┘
              │
              │ (실패 발생 시)
              │
              │            ┌──────────────┐
              └───────────>│COMPENSATING  │
                           └──────┬───────┘
                                  │
                                  │ Execute Compensation
                                  ▼
                           ┌──────────────┐
                           │   FAILED ❌  │
                           └──────────────┘
```

### 5.5 Orchestrator 핵심 책임

**1. Saga 실행 조율**
- 각 서비스 순차적 호출
- Step 성공/실패 판단
- 다음 Step 결정

**2. 상태 관리**
- Saga 상태 추적 (State Machine)
- Saga Log 저장 및 업데이트
- Step별 결과 기록

**3. 보상 트랜잭션 실행**
- 실패 지점 파악
- 역순으로 보상 트랜잭션 실행
- 보상 결과 검증

**4. 장애 복구**
- 서버 재시작 시 미완료 Saga 복구
- Timeout 처리
- 재시도 로직

**5. 모니터링 및 알림**
- Saga 실행 시간 측정
- 실패율 추적
- 알림 발송 (실패 시)

### 5.6 보상 트랜잭션 전략

**보상 가능한 트랜잭션 (Compensatable)**
- 이미 커밋된 트랜잭션을 논리적으로 취소
- 예: 재고 차감 → 재고 복구

**재시도 가능한 트랜잭션 (Retriable)**
- 일시적 오류로 실패한 경우 재시도
- 예: 네트워크 타임아웃, DB 커넥션 풀 고갈

**Pivot 트랜잭션**
- 성공하면 Saga는 완료까지 진행
- 실패하면 보상 시작
- 예: 결제 처리 (Payment 단계)

**보상 순서**
```
정상 실행 순서:
Order Created → Stock Deducted → Payment Completed → Order Completed

보상 실행 순서 (역순):
Order Canceled ← Stock Restored ← Payment Refunded
```

---

## 6. 데이터 일관성 보장 메커니즘

### 6.1 멱등성(Idempotency) 보장

**필요성**:
- 네트워크 재시도로 인한 중복 요청
- Message Queue 재전송
- 서비스 재시작 시 재처리

**구현 방법**:

**1) Idempotency Key 사용**
- 각 요청에 고유한 키 부여
- 이미 처리된 요청은 스킵

**2) ProcessedEvent 테이블**
- 처리 완료된 이벤트 ID 저장
- 이벤트 처리 전 중복 검사

**3) 데이터베이스 제약 조건**
- Unique 제약으로 중복 방지
- 예: `UNIQUE(orderId, productId)` for OrderItem

### 6.2 이벤트 순서 보장

**문제점**:
- 네트워크 지연으로 순서 뒤바뀜 가능
- 병렬 처리 시 순서 보장 어려움

**해결책**:

**1) Partition Key (Kafka)**
- 같은 Order ID는 같은 파티션으로 전송
- 파티션 내에서는 순서 보장

**2) Sequence Number**
- 이벤트에 순서 번호 부여
- 처리 전 순서 검증

**3) 버전 관리 (Optimistic Locking)**
- 엔티티에 버전 필드 추가
- 버전 불일치 시 재시도

### 6.3 분산 락 전략

**현재 사용**: Redis 기반 분산 락

**적용 대상**:
- 재고 차감 (Product)
- 포인트 차감 (User)
- 선착순 쿠폰 발급

**Lock Key 설계**:
```
재고 차감: "product:stock:{productId}"
포인트 차감: "user:point:{userId}"
쿠폰 발급: "coupon:issue:{couponId}"
```

**주의사항**:
- Lock 획득 실패 시 재시도 전략
- Lock Timeout 설정 (Deadlock 방지)
- Lock 해제 보장 (finally block)

### 6.4 Outbox Pattern 적용

**목적**:
- 이벤트 발행과 DB 저장의 원자성 보장
- 이벤트 유실 방지

**동작 방식**:
```
1. 비즈니스 로직 실행 + Outbox 테이블에 이벤트 저장 (같은 트랜잭션)
2. 트랜잭션 커밋
3. 별도 Publisher가 Outbox 테이블 폴링
4. 이벤트를 Message Queue로 발행
5. 발행 성공 시 Outbox 상태 업데이트 (PUBLISHED)
```

**Outbox 테이블 구조**:
- `id`: 이벤트 ID
- `aggregateType`: 도메인 타입 (Order, Product, etc.)
- `aggregateId`: 도메인 ID
- `eventType`: 이벤트 타입
- `payload`: 이벤트 데이터 (JSON)
- `status`: PENDING, PUBLISHED, FAILED
- `createdAt`: 생성 시간
- `publishedAt`: 발행 시간
- `retryCount`: 재시도 횟수

### 6.5 재시도 및 Timeout 전략

**재시도 정책**:
- 최대 재시도 횟수: 3회
- 재시도 간격: 지수 백오프 (1초 → 2초 → 4초)
- 재시도 대상: 일시적 오류 (네트워크, DB 커넥션)

**Timeout 정책**:
```
서비스 호출 Timeout:
- Order → Product: 5초
- Order → Payment: 10초 (외부 PG 연동 고려)
- Saga 전체 Timeout: 30초

Timeout 발생 시:
- 보상 트랜잭션 실행
- Saga 상태를 FAILED로 변경
- 알림 발송
```

### 6.6 데드레터 큐 (DLQ)

**목적**:
- 재시도 실패한 메시지 격리
- 수동 처리 또는 분석

**처리 방식**:
```
1. Message Queue에서 3회 재시도 실패
2. DLQ로 메시지 이동
3. 모니터링 알림 발송
4. 운영자 수동 확인 및 처리
   - 데이터 복구
   - 보상 트랜잭션 실행
   - 원인 분석 및 버그 수정
```

---

## 7. 결론 및 기대효과

### 7.1 문제 해결 요약

본 설계를 통해 MSA 환경에서 발생하는 9가지 핵심 문제를 해결할 수 있습니다:

| 문제 유형 | 문제점 | Saga Orchestration 해결 방안 |
|----------|--------|---------------------------|
| **원자성** | 부분 실패 | 자동 보상 트랜잭션 실행 |
| **원자성** | 네트워크 타임아웃 | Saga Log + 멱등성 키 |
| **원자성** | 분산 락 경합/데드락 | 중앙 집중식 락 관리 |
| **일관성** | 읽기 일관성 | 재고 예약 메커니즘 |
| **일관성** | 이벤트 중복 처리 | 멱등성 보장 |
| **격리성** | Dirty Read | 분산 락 + 순차 처리 |
| **지속성** | 서비스 장애 복구 | Saga Log 기반 자동 복구 |
| **성능** | 분산 오버헤드 | 비동기 처리 + 캐싱 |
| **운영** | 디버깅 어려움 | Saga Log 통합 추적 |

### 7.2 Saga Orchestration 패턴의 장점

**비즈니스 관점**:
- ✅ 데이터 일관성 보장으로 비즈니스 신뢰도 향상
- ✅ 자동 보상 트랜잭션으로 운영 부담 감소
- ✅ 장애 격리로 전체 시스템 다운 방지

**기술 관점**:
- ✅ 명확한 트랜잭션 경계
- ✅ 서비스별 독립적인 확장 가능
- ✅ 중앙 집중식 상태 관리로 복잡도 감소

**운영 관점**:
- ✅ Saga Log를 통한 통합 모니터링
- ✅ 장애 원인 파악 및 복구 용이
- ✅ 자동화된 복구 메커니즘
