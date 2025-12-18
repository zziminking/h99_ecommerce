# Kafka 기반 선착순 쿠폰 시스템 설계서

## 목차
1. [설계 개요](#1-설계-개요)
2. [토픽 설계](#2-토픽-설계)
3. [Producer 구조](#3-producer-구조)
4. [Consumer 구조](#4-consumer-구조)
5. [DLQ 처리 전략](#5-dlq-처리-전략)
6. [폴링 기반 상태 조회](#6-폴링-기반-상태-조회)
7. [전체 처리 흐름](#7-전체-처리-흐름)
8. [에러 처리 시나리오](#8-에러-처리-시나리오)
9. [구현 단계](#9-구현-단계)

---

## 1. 설계 개요

### 1.1 핵심 설계 결정

**동시성 보장:**
- Redis Lua Script로 선착순 처리 및 동시성 보장
- Kafka는 발급 완료 이벤트 전파 역할만 수행
- **Why?** Kafka는 메시지 순서만 보장하며, 10,000명이 동시에 100장 쿠폰을 요청할 때 정확히 100장만 발급을 보장할 수 없음. Redis의 원자적 연산이 필요.

**DLQ 처리:**
- 전용 DLQ Consumer 구현
- 자동 에러 분류 및 재처리
- **Why?** 일시적 오류(DB 연결 끊김 등)는 자동 복구가 가능하므로, 수동 개입 없이 시스템이 자동으로 재처리하도록 함.

**사용자 알림:**
- 폴링 방식의 상태 조회 API
- Redis에 처리 상태 저장 (TTL: 10분)
- **Why?** WebSocket은 인프라 복잡도가 높고, 폴링은 구현이 간단하며 모든 브라우저에서 지원됨. 쿠폰 발급은 1-2초 내 완료되므로 폴링이 적합.

### 1.2 구성 요소

```
[구현할 컴포넌트]
1. CouponKafkaPublisher (이벤트 발행)
2. CouponEventProducer (Kafka 전송)
3. CouponDatabaseSyncConsumer (DB 동기화)
4. CouponNotificationConsumer (알림 발송)
5. CouponStatisticsConsumer (통계 수집)
6. CouponDLQConsumer (DLQ 처리)
7. CouponStatusController (상태 조회 API)
8. DLQRetryScheduler (DLQ 재처리 스케줄러)
```

### 1.3 시스템 제약사항

**성능 요구사항:**
- 동시 접속: 10,000명
- 처리 시간: 평균 1-2초 (Redis 발급 → DB 동기화 완료)
- Consumer Lag: 1,000개 이하 유지

**데이터 정합성:**
- Redis 발급 완료 = DB 저장 보장 (Eventually Consistent)
- 중복 발급 방지 (Redis Sorted Set)
- 재고 정확도 100%

---

## 2. 토픽 설계

### 2.1 토픽 구조

#### Topic 1: `coupon-issued` (메인 토픽)

**설정값:**
```yaml
토픽명: coupon-issued
파티션: 3
복제 계수: 2
보관 기간: 7일 (604800000ms)
압축: lz4
```

**파티션 키 전략:**
- 쿠폰 ID를 키로 사용
- 같은 쿠폰에 대한 이벤트는 순서 보장
- **Why?** 쿠폰별 통계 집계 시 순서가 중요하며, 같은 쿠폰 이벤트를 동일한 파티션에 저장하여 순차 처리

**메시지 스키마:**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": 1001,
  "couponId": 500,
  "issuedAt": 1702345678901,
  "rank": 45,
  "couponName": "신규가입 10% 할인쿠폰",
  "discountType": "PERCENTAGE",
  "discountValue": 10
}
```

#### Topic 2: `coupon-issued-dlq` (Dead Letter Queue)

**설정값:**
```yaml
토픽명: coupon-issued-dlq
파티션: 1 (순서 보장)
복제 계수: 3 (중요도 높음)
보관 기간: 30일 (2592000000ms)
```

**DLQ 메시지 스키마:**
```json
{
  "originalMessage": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "userId": 1001,
    "couponId": 500,
    ...
  },
  "errorMessage": "Database connection timeout",
  "errorStackTrace": "java.sql.SQLException: Connection timeout...",
  "failedAt": 1702345680000,
  "retryCount": 3,
  "consumerGroup": "coupon-db-sync",
  "partition": 0,
  "offset": 12345
}
```

**Why DLQ는 단일 파티션?**
- DLQ는 실패한 메시지를 순차적으로 처리
- 에러 분석 및 재처리 시 순서가 중요
- 처리량이 메인 토픽보다 훨씬 적음

**Why 복제 계수 3?**
- DLQ 메시지 손실 시 데이터 복구 불가능
- 높은 가용성 보장 필요

### 2.2 KafkaConfig 설정

**파일 위치:** `src/main/java/h99/ecommerce/config/KafkaConfig.java`

**필요한 Bean:**
- `couponIssuedTopic()`: 메인 토픽 생성
- `couponIssuedDLQTopic()`: DLQ 토픽 생성

**토픽 상수 정의:**
```
COUPON_ISSUED_TOPIC = "coupon-issued"
COUPON_ISSUED_DLQ_TOPIC = "coupon-issued-dlq"
```

---

## 3. Producer 구조

### 3.1 구현 구조

```
CouponService
    ↓ publishEvent()
CouponKafkaPublisher (@TransactionalEventListener)
    ↓ publishToKafka()
CouponEventProducer (KafkaTemplate)
    ↓ send()
Kafka Broker
```

### 3.2 각 레이어의 역할

**CouponService:**
- Redis Lua Script로 쿠폰 발급
- requestId 생성 (UUID)
- Redis에 초기 상태 저장 (`coupon:issue:status:{requestId}` = "PROCESSING")
- Spring Event 발행 (CouponIssuedEvent)
- 응답 반환 (202 Accepted)

**CouponKafkaPublisher:**
- `@TransactionalEventListener(phase = AFTER_COMMIT)`로 동작
- **Why?** DB 트랜잭션이 완전히 커밋된 후에만 Kafka로 발행
- 비동기 실행 (`@Async`)
- 실패 시 로그 기록 및 보상 트랜잭션 고려

**CouponEventProducer:**
- KafkaTemplate을 사용하여 메시지 전송
- 동기 방식 전송 (5초 타임아웃)
- **Why 동기?** 발행 실패를 즉시 감지하여 보상 처리 가능

### 3.3 Event 스키마 변경

**CouponIssuedEvent 추가 필드:**
- `requestId`: 발급 요청 고유 ID (상태 추적용)
- `couponName`, `discountType`, `discountValue`: Consumer에서 알림 발송 시 사용

### 3.4 Async 설정

**ThreadPoolTaskExecutor 설정:**
```
Bean Name: couponEventExecutor
Core Pool Size: 5
Max Pool Size: 10
Queue Capacity: 100
Thread Prefix: coupon-event-
Rejected Policy: CallerRunsPolicy
```

**Why CallerRunsPolicy?**
- 큐가 가득 차면 호출 스레드에서 직접 실행
- 작업 손실 방지

---

## 4. Consumer 구조

### 4.1 Consumer 역할 정의

| Consumer | 역할 | Consumer Group | 중요도 | ACK 전략 |
|----------|------|----------------|--------|----------|
| CouponDatabaseSyncConsumer | Redis → RDB 동기화 | coupon-db-sync | 🔥 높음 | 수동 ACK |
| CouponNotificationConsumer | 발급 알림 발송 | coupon-notification | ⭐ 중간 | 자동 ACK |
| CouponStatisticsConsumer | 통계 수집 | coupon-statistics | ⭐ 중간 | 자동 ACK |

**Why 독립적인 Consumer Group?**
- 각 Consumer가 동일한 메시지를 독립적으로 처리
- DB 동기화 실패해도 알림 발송과 통계 수집은 정상 작동
- 각 Consumer의 처리 속도가 다르므로 독립적인 Offset 관리 필요

### 4.2 Consumer 공통 설정

**KafkaConsumerConfig 역할:**
- ConcurrentKafkaListenerContainerFactory 설정
- Concurrency: 3 (파티션 수와 동일)
- ACK Mode: RECORD (메시지 단위 커밋)
- ErrorHandler: DefaultErrorHandler with Exponential Backoff

**에러 핸들러 설정:**
```
재시도 전략: Exponential Backoff
- 1초 → 2초 → 4초 (총 3회)

재시도하지 않을 예외:
- IllegalArgumentException
- NullPointerException
- JsonProcessingException

재시도 후 실패 시: DLQ로 이동
```

**Why Exponential Backoff?**
- 일시적 오류(네트워크 지연, DB 커넥션 부족)는 시간이 지나면 복구됨
- 즉각 재시도하면 시스템 부하만 증가
- 간격을 늘려가며 재시도하여 복구 확률 향상

### 4.3 CouponDatabaseSyncConsumer (DB 동기화)

**역할:**
1. Kafka 메시지 수신
2. DB에 UserCoupon 저장
3. Redis 상태 업데이트 (COMPLETED)
4. 수동 ACK

**에러 처리:**
- 예외 발생 시 Redis 상태를 FAILED로 업데이트
- 예외를 다시 throw하여 재시도 또는 DLQ 이동

**Why 수동 ACK?**
- DB 저장 성공 후에만 커밋
- 메시지 손실 방지

### 4.4 CouponNotificationConsumer (알림 발송)

**역할:**
1. Push 알림 발송 (SMS, Email, Push)
2. 실패해도 재시도하지 않음 (비즈니스적으로 덜 중요)

**Why 재시도 안 함?**
- 알림은 보조 기능
- 실패해도 사용자는 폴링으로 상태 확인 가능
- 알림 서비스 장애가 전체 시스템에 영향 주는 것 방지

### 4.5 CouponStatisticsConsumer (통계 수집)

**역할:**
1. Redis에 통계 데이터 저장
   - 일별 발급 수: `coupon:stats:daily:{날짜}:{쿠폰ID}`
   - 시간별 발급 수: `coupon:stats:hourly:{날짜-시간}:{쿠폰ID}`
2. TTL 설정 (일별: 30일, 시간별: 7일)

**Why Redis?**
- 실시간 통계 조회 가능
- 높은 처리량 (초당 수천 건 증가 연산)

---

## 5. DLQ 처리 전략

### 5.1 DLQ 처리 흐름

```
[Consumer 실패]
    ↓
[ErrorHandler 재시도 3회]
    ↓
[DLQ로 메시지 이동]
    ↓
[CouponDLQConsumer 수신]
    ↓
[에러 분류]
    ↓
┌─────────┴─────────┐
↓                   ↓
TEMPORARY        PERMANENT
(일시적 오류)    (영구적 오류)
↓                   ↓
재시도 횟수 확인   에러 로그 저장
< 5회: 5분 후     관리자 알림
>= 5회: DDLQ     상태: FAILED
```

### 5.2 에러 분류 기준

**TEMPORARY (일시적 오류):**
- Connection timeout
- Connection refused
- Too many connections
- SocketTimeoutException
- Database connection failed

**처리:** 최대 5회까지 5분 간격으로 재시도

**PERMANENT (영구적 오류):**
- NullPointerException
- IllegalArgumentException
- JsonProcessingException
- Data validation failed
- Duplicate key

**처리:** 재시도하지 않고 즉시 관리자 알림

**UNKNOWN (알 수 없는 오류):**
- 위 패턴에 매칭되지 않는 오류

**처리:** 로그 저장 후 관리자 확인 필요

### 5.3 재처리 메커니즘

**DLQConsumer:**
- DLQ 토픽 구독
- 에러 분류 로직 실행
- TEMPORARY 오류 시 Redis에 저장 (TTL: 5분)
- Redis Key: `coupon:dlq:scheduled:{requestId}`

**DLQRetryScheduler:**
- 매 1분마다 실행
- Redis에서 재처리 대상 조회 (`coupon:dlq:scheduled:*`)
- DB 동기화 Consumer 로직 재실행
- 성공 시 Redis 키 삭제

**재시도 카운터:**
- Redis Key: `coupon:dlq:retry:{requestId}`
- 재시도할 때마다 증가
- 5회 이상 시 DDLQ로 이동

### 5.4 DDLQ (Dead Dead Letter Queue)

**용도:**
- 5회 재시도 후에도 실패한 메시지 보관
- 수동 처리 대기

**저장 위치:**
- Redis Key: `coupon:ddlq:{requestId}`
- TTL: 90일

**데이터 구조:**
```json
{
  "event": { /* 원본 이벤트 */ },
  "reason": "최대 재시도 횟수 초과",
  "savedAt": 1702345678901
}
```

### 5.5 관리자 알림

**알림 조건:**
- PERMANENT 오류 발생
- 최대 재시도 횟수 초과 (DDLQ 이동)
- UNKNOWN 오류 발생

**알림 채널:**
- Slack
- Email
- SMS (긴급)

---

## 6. 폴링 기반 상태 조회

### 6.1 API 설계

**Endpoint:**
```
GET /api/coupons/issue-status/{requestId}
```

**Response:**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "userCouponId": 12345,
  "error": null,
  "createdAt": 1702345678901,
  "updatedAt": 1702345680123
}
```

**상태 값:**
- `PROCESSING`: 처리 중
- `COMPLETED`: 완료 (userCouponId 포함)
- `FAILED`: 실패 (error 메시지 포함)

### 6.2 Redis 상태 저장

**Key 패턴:**
```
coupon:issue:status:{requestId}
```

**TTL:** 10분

**Why 10분?**
- 대부분 1-2초 내 처리 완료
- 실패 시에도 5분 내 재처리
- 10분이면 충분하며, 메모리 절약

### 6.3 프론트엔드 폴링 전략

**폴링 간격:** 1초
**최대 폴링 시간:** 30초
**폴링 중단 조건:**
- status === "COMPLETED"
- status === "FAILED"
- 30초 타임아웃

**타임아웃 시 처리:**
- "처리 시간이 초과되었습니다. 마이페이지에서 확인해주세요." 메시지 표시

### 6.4 폴링 vs WebSocket vs Callback 비교

| 방식 | 장점 | 단점 | 선택 이유 |
|------|------|------|----------|
| 폴링 | 구현 간단, 브라우저 호환성 | 불필요한 요청 | ✅ 처리 시간 짧음 (1-2초), 구현 복잡도 낮음 |
| WebSocket | 실시간 전송, 서버 푸시 | 인프라 복잡, 연결 유지 | ❌ 쿠폰 발급에 과한 스펙 |
| Callback | 서버 부하 적음 | 콜백 구현 필요, 실패 처리 복잡 | ❌ 모바일 앱 등 다양한 클라이언트 지원 어려움 |

---

## 7. 전체 처리 흐름

### 7.1 정상 처리 흐름

```
[사용자] → POST /api/coupons/{id}/issue?userId=xxx
              ↓
┌─────────────────────────────────────────────────┐
│ [Step 1] CouponService                          │
│                                                 │
│ 1-1. Redis Lua Script 실행                     │
│      - 재고 확인 및 차감                        │
│      - 중복 발급 체크                           │
│      - Sorted Set에 순위 기록                   │
│                                                 │
│ 1-2. requestId 생성 (UUID)                      │
│                                                 │
│ 1-3. Redis 상태 저장 (PROCESSING)              │
│      Key: coupon:issue:status:{requestId}      │
│                                                 │
│ 1-4. Spring Event 발행                          │
└─────────────┬───────────────────────────────────┘
              ↓
      [202 Accepted 응답]
      { requestId, status: "PROCESSING", rank, remainingStock }
              ↓
┌─────────────────────────────────────────────────┐
│ [Step 2] CouponKafkaPublisher                   │
│ @TransactionalEventListener(AFTER_COMMIT)       │
│                                                 │
│ 2-1. 비동기 실행 (@Async)                       │
│ 2-2. CouponEventProducer 호출                   │
└─────────────┬───────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────┐
│ [Step 3] CouponEventProducer                    │
│                                                 │
│ 3-1. KafkaTemplate.send()                       │
│      - Topic: coupon-issued                     │
│      - Key: couponId                            │
│      - 동기 전송 (5초 타임아웃)                  │
└─────────────┬───────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────┐
│ [Step 4] Kafka Broker                           │
│ - 파티션 분산 저장                              │
│ - 복제본 생성 (replication: 2)                  │
└─────────────┬───────────────────────────────────┘
              ↓
     [Consumer들이 병렬 처리]
              ↓
    ┌─────────┴─────────┬─────────────┐
    ↓                   ↓             ↓
┌──────────┐    ┌───────────┐  ┌──────────┐
│Consumer 1│    │Consumer 2 │  │Consumer 3│
│DB 동기화 │    │알림 발송  │  │통계 수집 │
└────┬─────┘    └─────┬─────┘  └────┬─────┘
     ↓                ↓              ↓
Redis 상태        Push 알림      Redis 통계
업데이트          발송            증가
(COMPLETED)
     ↓
┌─────────────────────────────────────────────────┐
│ [Step 5] 사용자 폴링                            │
│                                                 │
│ 5-1. GET /api/coupons/issue-status/{requestId} │
│      (1초마다 반복, 최대 30초)                  │
│                                                 │
│ 5-2. status === "COMPLETED" 확인               │
│                                                 │
│ 5-3. 완료 메시지 표시, 쿠폰 목록 새로고침       │
└─────────────────────────────────────────────────┘
```

### 7.2 에러 처리 흐름

```
[Consumer 처리 중 예외 발생]
         ↓
┌─────────────────────────────────────────────────┐
│ [Step 1] ErrorHandler - 자동 재시도             │
│ - Exponential Backoff (1초 → 2초 → 4초)        │
└─────────────┬───────────────────────────────────┘
              ↓
        재시도 성공?
              ↓
      ┌───────┴────────┐
      ↓                ↓
    성공             3회 실패
      ↓                ↓
  [정상 처리]   ┌──────────────────┐
               │ DLQ로 메시지 이동│
               │ Topic: coupon-   │
               │   issued-dlq     │
               └────────┬─────────┘
                        ↓
               ┌──────────────────┐
               │ CouponDLQConsumer│
               │ - 에러 분류      │
               └────────┬─────────┘
                        ↓
              ┌─────────┴──────────┐
              ↓                    ↓
        TEMPORARY              PERMANENT
       (일시적 오류)          (영구적 오류)
              ↓                    ↓
    ┌──────────────────┐  ┌──────────────────┐
    │ 재시도 횟수 확인 │  │ 관리자 알림      │
    │                  │  │ Redis: FAILED    │
    │ < 5회:           │  │ 수동 처리 대기   │
    │ - Redis 저장     │  └──────────────────┘
    │ - 5분 후 재처리  │
    │                  │
    │ >= 5회:          │
    │ - DDLQ 이동      │
    │ - 관리자 알림    │
    └────────┬─────────┘
             ↓
    ┌──────────────────┐
    │ DLQ Scheduler    │
    │ (매 1분)         │
    │ - 재처리 시도    │
    └──────────────────┘
```

---

## 8. 에러 처리 시나리오

### 8.1 시나리오 1: DB 연결 타임아웃 (일시적 오류)

**상황:**
```
Consumer가 UserCoupon을 DB에 저장하려다 연결 타임아웃 발생
```

**처리 과정:**
1. ErrorHandler가 1초, 2초, 4초 간격으로 3회 재시도
2. 3회 모두 실패 → DLQ로 이동
3. DLQConsumer가 "Connection timeout" 감지 → TEMPORARY로 분류
4. 재시도 횟수 확인 (현재 1회)
5. Redis에 저장 (`coupon:dlq:scheduled:{requestId}`, TTL: 5분)
6. 5분 후 DLQScheduler가 재처리 시도
7. DB 연결 복구 → 처리 성공
8. Redis 상태 업데이트: COMPLETED

**결과:**
- ✅ 사용자 영향 없음 (자동 복구)
- ✅ 약 5-6분 지연

### 8.2 시나리오 2: NULL 값으로 인한 NPE (영구적 오류)

**상황:**
```
이벤트의 userId가 null → NullPointerException 발생
```

**처리 과정:**
1. ErrorHandler가 재시도 불가능 예외로 판단 (설정에 명시)
2. 즉시 DLQ로 이동 (재시도 없음)
3. DLQConsumer가 "NullPointerException" 감지 → PERMANENT로 분류
4. 에러 로그 저장 (Redis, 30일 보관)
5. 관리자에게 Slack/Email 알림
6. Redis 상태 업데이트: FAILED

**결과:**
- ❌ 수동 처리 필요
- ❌ 사용자에게 실패 알림

### 8.3 시나리오 3: Kafka 브로커 다운

**상황:**
```
CouponEventProducer가 Kafka로 전송 시도 → 브로커 응답 없음
```

**처리 과정:**
1. KafkaTemplate.send()가 5초 타임아웃
2. TimeoutException 발생
3. CouponKafkaPublisher에서 catch
4. 로그 기록
5. Redis 상태는 여전히 PROCESSING (업데이트 실패)

**문제:**
- ⚠️ Redis에는 발급 완료, DB에는 미동기
- ⚠️ 데이터 정합성 문제

**해결 방안:**

**Option 1: 재발행 API 제공**
- 관리자가 수동으로 이벤트 재발행
- 간단하지만 수동 작업 필요

**Option 2: 배치 동기화**
- 매일 Redis와 DB 비교하여 누락 데이터 동기화
- 자동화되지만 실시간 동기화 불가

**Option 3: Outbox Pattern 적용 (권장)**
- DB에 이벤트 테이블 추가
- 트랜잭션으로 묶어서 저장 후 발행
- 데이터 정합성 보장, 구현 복잡도 증가

---

## 9. 구현 단계

### Phase 1: 토픽 및 Producer 구현

**작업 목록:**
- [ ] KafkaConfig 구현 (토픽 Bean 정의)
- [ ] CouponIssuedEvent에 requestId, couponName 등 필드 추가
- [ ] CouponKafkaPublisher 구현 (@TransactionalEventListener)
- [ ] CouponEventProducer 구현 (KafkaTemplate 사용)
- [ ] AsyncConfig 설정 (couponEventExecutor)
- [ ] CouponService 수정
  - requestId 생성
  - Redis 상태 저장 로직 추가
  - 응답 객체 수정 (202 Accepted)

**검증 방법:**
- Kafka CLI로 토픽 존재 확인
- Kafka CLI로 메시지 수신 확인
- 로그로 발행 성공 확인

### Phase 2: Consumer 구현

**작업 목록:**
- [ ] KafkaConsumerConfig 구현
  - ErrorHandler 설정
  - Exponential Backoff 설정
  - 재시도 불가능 예외 정의
- [ ] CouponDatabaseSyncConsumer 구현
  - DB 저장 로직
  - Redis 상태 업데이트
  - 수동 ACK
- [ ] CouponNotificationConsumer 구현
  - 알림 발송 로직 (Mock)
- [ ] CouponStatisticsConsumer 구현
  - Redis 통계 증가
- [ ] Consumer별 단위 테스트

**검증 방법:**
- Consumer Lag 모니터링 (Kafka CLI)
- 각 Consumer별 로그 확인
- Redis 데이터 확인

### Phase 3: DLQ 처리

**작업 목록:**
- [ ] DLQ 토픽 생성 (KafkaConfig에 Bean 추가)
- [ ] CouponDLQConsumer 구현
  - 에러 분류 로직 (TEMPORARY, PERMANENT, UNKNOWN)
  - Redis 재처리 스케줄링
  - 관리자 알림 (Mock)
- [ ] DLQRetryScheduler 구현
  - Redis 스캔 및 재처리
- [ ] DDLQ 저장 로직
- [ ] 관리자 알림 서비스 연동 (Slack, Email)

**검증 방법:**
- 강제로 예외 발생시켜 DLQ 이동 확인
- DLQ 메시지 스키마 확인
- 재처리 로직 동작 확인

### Phase 4: 폴링 API

**작업 목록:**
- [ ] CouponStatusController 구현
  - GET /api/coupons/issue-status/{requestId}
- [ ] CouponIssueStatusResponse DTO
- [ ] CouponIssueResponse 수정 (requestId 포함)
- [ ] 프론트엔드 폴링 로직 구현
- [ ] API 문서 작성 (Swagger)

**검증 방법:**
- Postman으로 API 호출 테스트
- 프론트엔드 폴링 동작 확인
- Redis TTL 확인

---

**작성일:** 2025-01-XX
**작성자:** 개발팀
**버전:** 3.0