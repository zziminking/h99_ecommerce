-- K6 부하 테스트를 위한 테스트 데이터 생성 스크립트

-- 1. 기존 테스트 데이터 삭제 (필요한 경우)
-- DELETE FROM user_coupon WHERE coupon_id = 1;
-- DELETE FROM coupon WHERE coupon_id = 1;

-- 2. 테스트용 쿠폰 생성
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
) ON DUPLICATE KEY UPDATE
    max_issue_count = 1000,
    status = 'ACTIVE',
    updated_at = NOW();

-- 3. Stress Test용 쿠폰 (재고 많음)
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
) ON DUPLICATE KEY UPDATE
    max_issue_count = 5000,
    status = 'ACTIVE',
    updated_at = NOW();

-- 4. Soak Test용 쿠폰 (재고 매우 많음)
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
) ON DUPLICATE KEY UPDATE
    max_issue_count = 10000,
    status = 'ACTIVE',
    updated_at = NOW();

-- 5. 테스트 사용자 생성 (1 ~ 1000번)
-- 주의: 사용자가 이미 존재하는 경우 스킵

-- 확인 쿼리
SELECT
    coupon_id,
    name,
    discount_type,
    discount_value,
    max_issue_count,
    status
FROM coupon
WHERE coupon_id IN (1, 2, 3);