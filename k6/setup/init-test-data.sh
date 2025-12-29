#!/bin/bash

# 테스트 데이터 초기화 스크립트

set -e

echo "=========================================="
echo "테스트 데이터 초기화"
echo "=========================================="

# 1. 애플리케이션 실행 확인
echo ""
echo "애플리케이션 실행 중..."

# 백그라운드에서 애플리케이션 실행 (이미 실행 중이면 스킵)
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "애플리케이션이 실행되지 않았습니다."
    echo "새 터미널에서 다음 명령을 실행하세요:"
    echo "  ./gradlew bootRun"
    echo ""
    read -p "애플리케이션이 실행되면 Enter를 누르세요..."
fi

# 2. Redis 데이터 초기화
echo ""
echo "Redis 데이터 초기화..."
if command -v redis-cli &> /dev/null; then
    # 테스트 쿠폰 관련 키 삭제
    redis-cli DEL coupon:stock:1 2>/dev/null || true
    redis-cli DEL coupon:meta:1 2>/dev/null || true
    redis-cli DEL coupon:issued:1 2>/dev/null || true
    redis-cli DEL coupon:issue:log:1 2>/dev/null || true
    redis-cli DEL coupon:issue:counter:1 2>/dev/null || true

    redis-cli DEL coupon:stock:2 2>/dev/null || true
    redis-cli DEL coupon:meta:2 2>/dev/null || true
    redis-cli DEL coupon:issued:2 2>/dev/null || true
    redis-cli DEL coupon:issue:log:2 2>/dev/null || true
    redis-cli DEL coupon:issue:counter:2 2>/dev/null || true

    redis-cli DEL coupon:stock:3 2>/dev/null || true
    redis-cli DEL coupon:meta:3 2>/dev/null || true
    redis-cli DEL coupon:issued:3 2>/dev/null || true
    redis-cli DEL coupon:issue:log:3 2>/dev/null || true
    redis-cli DEL coupon:issue:counter:3 2>/dev/null || true

    echo "Redis 키 삭제 완료"
else
    echo "redis-cli를 찾을 수 없습니다. 수동으로 Redis를 초기화하세요."
fi

# 3. DB 테스트 데이터 생성 안내
echo ""
echo "=========================================="
echo "다음 단계:"
echo "=========================================="
echo ""
echo "1. MySQL에 접속하여 테스트 데이터를 생성하세요:"
echo "   mysql -u root -p h99_ecommerce < k6/test-data.sql"
echo ""
echo "2. 또는 애플리케이션 코드로 쿠폰을 생성하세요:"
echo ""
cat <<'EOF'
// CouponService를 통한 쿠폰 생성 예시
Coupon coupon = Coupon.builder()
    .name("K6 부하테스트 쿠폰")
    .discountType(DiscountType.FIXED)
    .discountValue(10000L)
    .maxIssueCount(1000)
    .startAt(LocalDateTime.of(2024, 1, 1, 0, 0))
    .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
    .build();

couponService.createCoupon(coupon);
EOF

echo ""
echo "3. 쿠폰 생성이 완료되면 Redis에 자동으로 초기화됩니다."
echo ""
echo "4. k6 테스트를 실행하세요:"
echo "   k6 run k6/smoke-test.js"
echo ""
