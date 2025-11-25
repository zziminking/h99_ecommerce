# 동시성 테스트 보고서

### 1.1 테스트 대상
- **쿠폰 발급**: 선착순 한정 쿠폰의 동시 발급 처리
- **상품 재고**: 다수 사용자의 동시 재고 차감 처리

### 1.2 목표
- Over-selling/Over-issuing 방지
- 데이터 정합성 보장
- 적절한 성능 확보

---

## 2. 동시성 제어 방식 선택 이유

### 2.1 쿠폰 발급 → Pessimistic Lock 선택

**도메인 특성:**
- **선착순 한정 수량**: 정확히 N명에게만 발급 (예: 100개 한정)
- **절대적 정확성 요구**: 초과 발급 시 금전적 손실 발생
- **높은 동시 요청**: 인기 쿠폰의 경우 동시 접근 빈도가 매우 높음
- **복합 작업**: 쿠폰 정보 조회 → 발급 가능 여부 검증 → 발급 횟수 증가 → UserCoupon 생성

**Pessimistic Lock을 선택한 이유:**

1. **충돌 빈도가 높은 핫스팟**
   - 모든 사용자가 동일한 쿠폰 레코드에 접근
   - Optimistic Lock 사용 시 대부분의 트랜잭션이 충돌로 실패
   - 재시도 오버헤드가 Lock 대기보다 비효율적

2. **비즈니스 임계성**
   - 초과 발급은 절대 허용 불가 (예: 100개 쿠폰이 101개 발급되면 안됨)
   - 실패한 사용자에게 명확한 "품절" 응답 제공

**구현 위치:** `JpaCouponRepository.java:40-48`
```java
public Coupon findByIdWithLock(Long couponId) {
    return em.createQuery(
            "SELECT c FROM Coupon c WHERE c.couponId = :couponId",
            Coupon.class)
        .setParameter("couponId", couponId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)  // SELECT ... FOR UPDATE
        .setHint("javax.persistence.lock.timeout", 3000)
        .getSingleResult();
}
```

**트레이드오프:**
- ✅ 정확성 보장
- ⚠️ Lock 대기 시간 발생 (하지만 선착순 특성상 불가피)

---

### 2.2 상품 재고 차감 → Conditional UPDATE 선택

**도메인 특성:**
- **다양한 상품 분산**: 100개 상품이 있으면 동시 요청이 분산됨
- **단순한 연산**: 수량 차감만 수행 (복잡한 검증 로직 없음)
- **상대적으로 낮은 충돌**: 쿠폰에 비해 동일 상품 동시 구매 확률 낮음
- **다중 상품 주문**: 한 주문에 여러 상품 → Deadlock 위험

**Conditional UPDATE를 선택한 이유:**

1. **Lock 없는 원자적 연산**
   ```sql
   UPDATE Product
   SET stock = stock - 10
   WHERE product_id = 1 AND stock >= 10
   ```
   - 조건 검사와 업데이트가 하나의 SQL로 원자적 실행
   - Lock 획득/해제 오버헤드 없음

2. **성능 최적화**
   - 대기 시간 없음 (Lock-free)
   - 실패 시에만 재시도 (최대 5회, 50ms 간격)
   - 성공 시 즉시 반환

3. **Deadlock 회피**
   - 여러 상품 동시 주문 시 Lock 순서 문제 발생 가능
   - Conditional UPDATE는 Lock을 사용하지 않아 Deadlock 위험 없음

4. **재고 특성**
   - 쿠폰처럼 모든 사용자가 동일 레코드에 몰리지 않음
   - 재고 부족 시 사용자에게 명확한 에러 메시지 제공

**구현 위치:** `JpaProductRepository.java:44-53`
```java
public int deductStockConditional(Long productId, int quantity) {
    return em.createQuery(
            "UPDATE Product a "
            + "SET a.stock.quantity = a.stock.quantity - :quantity "
            + "WHERE a.productId = :productId "
            + "AND a.stock.quantity >= :quantity")  // 원자적 조건 검사
        .setParameter("productId", productId)
        .setParameter("quantity", quantity)
        .executeUpdate();
}
```

**재시도 전략:** `ProductService.java:76-99`
```java
- 최대 5회 재시도
- 실패 시 50ms 대기 후 재시도
- 재고 부족이 확실하면 즉시 예외 발생 (재시도 X)
```

**트레이드오프:**
- ✅ 높은 성능 (Lock-free)
- ✅ Deadlock 위험 없음
- ⚠️ 재시도 로직 필요 (하지만 충돌 확률이 낮아 대부분 1회 성공)

---

### 2.3 Optimistic Lock을 선택하지 않은 이유

**Optimistic Lock 개요:**
- JPA `@Version` 어노테이션으로 구현
- 트랜잭션 커밋 시점에 버전 충돌 검증
- 충돌 시 `OptimisticLockException` 발생 → 재시도 필요

**쿠폰 발급에 적용하지 않은 이유:**

1. **매우 높은 충돌률**
   - 101명이 동일한 쿠폰 레코드 접근 → 충돌률 90% 이상 예상
   - 대부분의 트랜잭션이 실패 후 재시도
   - 재시도 오버헤드 > Lock 대기 시간

2. **사용자 경험 저하**
   - 빠른 실패 후 재시도 → 응답 시간 불규칙
   - 여러 번 재시도해도 실패 가능 (선착순 100명 마감 후)
   - Pessimistic Lock: 대기 후 확실한 성공/실패 응답

3. **서버 부하 증가**
   - 101명 × 평균 재시도 5회 = 500+ 트랜잭션
   - Pessimistic Lock: 101개 트랜잭션으로 처리

**상품 재고에 적용하지 않은 이유:**

1. **Conditional UPDATE가 더 간단**
   - Optimistic Lock: Entity 조회 + Version 체크 + 업데이트
   - Conditional UPDATE: 단일 UPDATE 쿼리로 완료

2. **재시도 로직이 동일**
   - 두 방식 모두 실패 시 재시도 필요
   - Conditional UPDATE가 구현이 더 직관적

3. **다중 상품 주문 시 복잡도**
   - 여러 상품의 Version을 모두 관리해야 함
   - Conditional UPDATE는 각 상품별로 독립적 처리

**적용 가능한 시나리오:**
- 충돌 빈도가 낮은 경우 (예: 다양한 레코드에 분산된 요청)
- 읽기 작업이 많고 쓰기가 적은 경우
- 간단한 JPA Entity 업데이트

---

## 3. 테스트 시나리오 및 결과

#### 테스트 1: 선착순 100개 한정 쿠폰 - 101명 동시 발급
```
Given:
  - 최대 발급 수량: 100개
  - 참여 사용자: 101명
  - 동시 요청 스레드: 32개

When:
  - 101명이 동시에 동일한 쿠폰 발급 요청

Then (예상 결과):
  - 성공: 100명 ✅
  - 실패: 1명 ✅
  - DB issuedCount: 100 ✅
  - UserCoupon 레코드: 100개 ✅

검증 항목:
  ✅ Over-issuing 방지 (101개 발급 방지)
  ✅ SELECT ... FOR UPDATE로 동시성 제어
  ✅ 정확히 선착순 100명만 성공
```

#### 테스트 2: 30개 한정 쿠폰 - 50명 동시 발급
```
Given:
  - 최대 발급 수량: 30개
  - 참여 사용자: 50명
  - 동시 요청 스레드: 32개

When:
  - 50명이 동시에 30개 한정 쿠폰 발급 요청

Then (예상 결과):
  - 성공: 30명 ✅
  - 실패: 20명 ✅
  - DB issuedCount: 30 ✅

검증 항목:
  ✅ 선착순 정확성
  ✅ 최대 발급 수량 엄수
```

#### 테스트 3: 품절 쿠폰 - 20명 동시 발급
```
Given:
  - 최대 발급 수량: 10개
  - 현재 발급 수량: 10개 (이미 품절)
  - 참여 사용자: 20명

When:
  - 20명이 품절된 쿠폰 발급 요청

Then (예상 결과):
  - 성공: 0명 ✅
  - 실패: 20명 ✅
  - DB issuedCount: 10 (변화 없음) ✅

검증 항목:
  ✅ 품절 쿠폰 추가 발급 방지
  ✅ 모든 사용자에게 동일한 실패 응답
```

---

### 3.3 상품 재고 차감 동시성 테스트 (Conditional UPDATE)

#### 테스트 1: 101명이 동시에 1개씩 주문 - 재고 100개
```
Given:
  - 초기 재고: 100개
  - 참여 사용자: 101명
  - 주문 수량: 각 1개
  - 동시 요청 스레드: 32개

When:
  - 101명이 동시에 1개씩 재고 차감 요청

Then (실제 결과):
  - 성공: 100명 ✅
  - 실패: 1명 ✅
  - 최종 재고: 0개 ✅

검증 항목:
  ✅ Over-selling 방지 (101개 판매 방지)
  ✅ Conditional UPDATE로 원자적 차감
  ✅ 재고 0까지 정확히 차감
```

#### 테스트 2: 50명이 동시에 3개씩 주문 - 재고 100개
```
Given:
  - 초기 재고: 100개
  - 참여 사용자: 50명
  - 주문 수량: 각 3개
  - 총 요청 수량: 150개 (재고 초과 시나리오)

When:
  - 50명이 동시에 3개씩 재고 차감 요청

Then (실제 결과):
  - 성공한 주문 수 * 3 = 차감된 재고 ✅
  - 최종 재고 >= 0 ✅
  - 차감된 재고 + 최종 재고 = 100 ✅

검증 항목:
  ✅ 재고 일관성 검증 (초기 재고 = 성공 차감 + 최종 재고)
  ✅ 음수 재고 방지
  ✅ 다중 수량 주문 시 동시성 제어
```

#### 테스트 3: 10명이 동시에 15개씩 주문 - 재시도 로직 검증
```
Given:
  - 초기 재고: 100개
  - 참여 사용자: 10명
  - 주문 수량: 각 15개
  - 총 요청 수량: 150개
  - 재시도: 최대 5회, 50ms 간격

When:
  - 10명이 동시에 15개씩 재고 차감 요청

Then (실제 결과):
  - 성공: 6명 ✅ (100 ÷ 15 = 6.xxx)
  - 실패: 4명 ✅
  - 최종 재고: 10개 ✅ (100 - 6×15 = 10)

검증 항목:
  ✅ 재시도 로직으로 충돌 해결
  ✅ 정확한 재고 계산
  ✅ 재고 부족 시 적절한 예외 발생
```
