#!/bin/bash

# K6 부하 테스트 환경 설정 스크립트

set -e

echo "=========================================="
echo "K6 부하 테스트 환경 설정"
echo "=========================================="

# 1. k6 설치 확인
echo ""
echo "[1/4] k6 설치 확인..."
if ! command -v k6 &> /dev/null; then
    echo "k6가 설치되어 있지 않습니다."
    echo ""
    echo "macOS에서 설치하려면 다음 명령을 실행하세요:"
    echo "  brew install k6"
    echo ""
    echo "다른 OS의 경우 https://k6.io/docs/get-started/installation/ 참고"
    exit 1
else
    echo "k6 설치 확인: $(k6 version)"
fi

# 2. 애플리케이션 실행 확인
echo ""
echo "[2/4] 애플리케이션 실행 확인..."
BASE_URL=${BASE_URL:-http://localhost:8080}
MAX_RETRY=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRY ]; do
    if curl -s ${BASE_URL}/actuator/health > /dev/null 2>&1; then
        echo "애플리케이션 실행 확인: ${BASE_URL}"
        break
    else
        if [ $RETRY_COUNT -eq 0 ]; then
            echo "애플리케이션이 실행되지 않았습니다."
            echo "애플리케이션을 시작하세요: ./gradlew bootRun"
            echo "대기 중... (최대 ${MAX_RETRY}초)"
        fi
        RETRY_COUNT=$((RETRY_COUNT + 1))
        sleep 1
    fi
done

if [ $RETRY_COUNT -eq $MAX_RETRY ]; then
    echo "애플리케이션 시작 대기 시간 초과"
    exit 1
fi

# 3. Redis 연결 확인
echo ""
echo "[3/4] Redis 연결 확인..."
if command -v redis-cli &> /dev/null; then
    if redis-cli ping > /dev/null 2>&1; then
        echo "Redis 연결 확인: OK"
    else
        echo "Redis에 연결할 수 없습니다."
        echo "Redis를 시작하세요: docker-compose up -d redis"
        exit 1
    fi
else
    echo "redis-cli가 설치되어 있지 않아 Redis 연결을 확인할 수 없습니다."
    echo "Redis가 실행 중인지 확인하세요."
fi

# 4. 테스트 데이터 생성
echo ""
echo "[4/4] 테스트 데이터 생성..."

# 쿠폰 ID 설정
COUPON_ID=${COUPON_ID:-1}

# 기존 쿠폰 확인
COUPON_CHECK=$(curl -s ${BASE_URL}/api/coupons/${COUPON_ID} 2>/dev/null || echo "")

if echo "$COUPON_CHECK" | grep -q "couponId"; then
    echo "쿠폰 ID ${COUPON_ID}가 이미 존재합니다."
    echo "기존 쿠폰을 사용합니다."
else
    echo "쿠폰 ID ${COUPON_ID}를 찾을 수 없습니다."
    echo ""
    echo "테스트용 쿠폰을 수동으로 생성해주세요:"
    echo ""
    echo "1. DB에 직접 쿠폰 데이터 삽입:"
    echo "   INSERT INTO coupon (coupon_id, name, discount_type, discount_value, max_issue_count, start_at, end_at, status)"
    echo "   VALUES (1, '테스트 쿠폰', 'FIXED', 10000, 1000, '2024-01-01 00:00:00', '2025-12-31 23:59:59', 'ACTIVE');"
    echo ""
    echo "2. 또는 CouponService.createCoupon() 메서드 호출"
    echo ""
fi

echo ""
echo "=========================================="
echo "환경 설정 완료!"
echo "=========================================="
echo ""
echo "테스트를 실행하려면 다음 명령을 사용하세요:"
echo ""
echo "  # Smoke Test"
echo "  k6 run k6/smoke-test.js"
echo ""
echo "  # Load Test"
echo "  k6 run k6/load-test.js"
echo ""
echo "  # Stress Test"
echo "  k6 run k6/stress-test.js"
echo ""
echo "  # Spike Test"
echo "  k6 run k6/spike-test.js"
echo ""
echo "  # Soak Test (30분)"
echo "  k6 run k6/soak-test.js"
echo ""
