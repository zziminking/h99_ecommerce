#!/bin/bash

# 테스트용 쿠폰 생성 스크립트

set -e

echo "=========================================="
echo "테스트용 쿠폰 생성"
echo "=========================================="

# MySQL 접속 정보 (환경변수 또는 기본값 사용)
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-h99_ecommerce}
DB_USER=${DB_USER:-root}
DB_PASS=${DB_PASS:-1234}

echo "MySQL 데이터베이스에 테스트 쿠폰을 생성합니다..."
echo "DB: $DB_HOST:$DB_PORT/$DB_NAME"

# MySQL 명령 실행
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" <<'EOF'
-- 기존 테스트 쿠폰 데이터 삭제
DELETE FROM user_coupon WHERE coupon_id IN (1, 2, 3);
DELETE FROM coupon WHERE coupon_id IN (1, 2, 3);

-- 테스트용 쿠폰 생성 (ID=1, Smoke/Load Test용)
INSERT INTO coupon (
    coupon_id,
    name,
    discount_type,
    discount_value,
    max_issue_count,
    start_at,
    end_at,
    status,
    created_at,
    updated_at
) VALUES (
    1,
    'K6 부하테스트 쿠폰',
    'FIXED',
    10000,
    1000,
    '2024-01-01 00:00:00',
    '2025-12-31 23:59:59',
    'ACTIVE',
    NOW(),
    NOW()
);

-- Stress Test용 쿠폰 (ID=2, 재고 많음)
INSERT INTO coupon (
    coupon_id,
    name,
    discount_type,
    discount_value,
    max_issue_count,
    start_at,
    end_at,
    status,
    created_at,
    updated_at
) VALUES (
    2,
    'K6 스트레스 테스트 쿠폰',
    'FIXED',
    5000,
    5000,
    '2024-01-01 00:00:00',
    '2025-12-31 23:59:59',
    'ACTIVE',
    NOW(),
    NOW()
);

-- Soak Test용 쿠폰 (ID=3, 재고 매우 많음)
INSERT INTO coupon (
    coupon_id,
    name,
    discount_type,
    discount_value,
    max_issue_count,
    start_at,
    end_at,
    status,
    created_at,
    updated_at
) VALUES (
    3,
    'K6 Soak 테스트 쿠폰',
    'PERCENTAGE',
    20,
    10000,
    '2024-01-01 00:00:00',
    '2025-12-31 23:59:59',
    'ACTIVE',
    NOW(),
    NOW()
);

-- 생성된 쿠폰 확인
SELECT
    coupon_id,
    name,
    discount_type,
    discount_value,
    max_issue_count,
    status
FROM coupon
WHERE coupon_id IN (1, 2, 3);
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "쿠폰 생성 완료!"
    echo "=========================================="
    echo ""
    echo "다음 단계:"
    echo "1. CouponService.createCoupon()을 호출하여 Redis에 초기화"
    echo "   또는 애플리케이션을 재시작하여 자동 초기화"
    echo ""
    echo "2. Redis 초기화를 수동으로 실행:"
    echo "   ./k6/init-redis.sh"
    echo ""
else
    echo ""
    echo "쿠폰 생성 실패!"
    echo "MySQL 접속 정보를 확인하세요."
    echo ""
    echo "환경변수 설정 예시:"
    echo "  export DB_HOST=localhost"
    echo "  export DB_PORT=3306"
    echo "  export DB_NAME=h99_ecommerce"
    echo "  export DB_USER=root"
    echo "  export DB_PASS=password"
    exit 1
fi