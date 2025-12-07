#!/bin/bash

echo "🔍 Redis 락 키 실시간 모니터링"
echo "========================================"

# Redis 컨테이너 찾기
echo "📡 Redis 컨테이너 검색 중..."
REDIS_CONTAINER=$(docker ps --format "{{.ID}}\t{{.Image}}" | grep redis | awk '{print $1}' | head -1)

if [ -z "$REDIS_CONTAINER" ]; then
    echo "❌ Redis 컨테이너를 찾을 수 없습니다."
    echo ""
    echo "💡 테스트를 먼저 실행해주세요:"
    echo "   ./gradlew test --tests RedisLockVisualizationTest"
    exit 1
fi

echo "✅ Redis 컨테이너 발견: $REDIS_CONTAINER"
echo "========================================"
echo ""

# Redis 정보 출력
echo "📊 Redis 연결 정보:"
REDIS_PORT=$(docker port $REDIS_CONTAINER 6379 | cut -d':' -f2)
echo "   Host: localhost"
echo "   Port: $REDIS_PORT"
echo ""

echo "🔑 현재 Redis에 저장된 모든 키:"
echo "========================================"
docker exec $REDIS_CONTAINER redis-cli KEYS "*"
echo ""

echo "🔒 락 관련 키만 필터링:"
echo "========================================"
docker exec $REDIS_CONTAINER redis-cli KEYS "*lock*" 2>/dev/null || echo "(락 키 없음)"
docker exec $REDIS_CONTAINER redis-cli KEYS "*product:stock:*" 2>/dev/null || echo "(재고 락 키 없음)"
echo ""

echo "💡 실시간 모니터링 시작 (Ctrl+C로 종료):"
echo "========================================"
echo "다른 터미널에서 테스트를 실행하세요:"
echo "  ./gradlew test --tests RedisLockVisualizationTest"
echo ""

# 실시간 모니터링
docker exec -i $REDIS_CONTAINER redis-cli MONITOR | grep --line-buffered -E "product:stock|lock"
