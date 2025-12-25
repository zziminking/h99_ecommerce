# 장애 대응 보고서: DB 커넥션 풀 고갈

## 목차
1. [장애 개요](#1-장애-개요)
2. [장애 타임라인](#2-장애-타임라인)
3. [장애 감지 및 초기 대응](#3-장애-감지-및-초기-대응)
4. [근본 원인 분석](#4-근본-원인-분석)
5. [복구 절차](#5-복구-절차)
6. [재발 방지 대책](#6-재발-방지-대책)

---

## 1. 장애 개요

### 1.1 기본 정보

| 항목 | 내용 |
|------|------|
| **발생 일시** | 2025-01-15 14:00:00 KST |
| **종료 일시** | 2025-01-15 14:15:32 KST |
| **지속 시간** | 약 15분 32초 |
| **심각도** | 🔥🔥🔥 Critical (Tier 1) |
| **영향 범위** | DB 동기화 완전 중단 |
| **작성자** | Backend Team |
| **작성일** | 2025-01-15 |

### 1.2 장애 요약

**선착순 쿠폰 이벤트 시작 직후 Kafka Consumer의 DB 커넥션 풀이 고갈되어 쿠폰 발급 데이터의 DB 동기화가 완전히 중단된 장애**

**주요 증상:**
- Kafka Consumer Lag 급증: 0 → 52,341 messages
- DB 동기화 지연: 1초 → 15분 이상
- HikariCP Connection timeout 에러 대량 발생
- 사용자가 발급받은 쿠폰이 DB에 저장되지 않음

**영향:**
- ✅ Redis 쿠폰 발급: 정상 동작 (1,000건 발급 완료)
- ❌ DB 저장: 완전 중단 (15분간 0건 저장)
- ❌ 사용자 경험: 쿠폰 발급 완료 → 마이페이지에서 조회 불가

### 1.3 피해 규모

```
총 발급 시도: 52,341건
Redis 발급 성공: 1,000건
DB 저장 성공 (장애 전): 128건
DB 저장 대기 (장애 중): 872건
최대 Consumer Lag: 52,341 messages
```

---

## 2. 장애 타임라인

### 2.1 전체 타임라인

```
14:00:00  선착순 이벤트 시작
14:00:05  트래픽 급증 (VU 10 → 1000)
14:00:12  Consumer Lag 1,000 돌파
14:00:23  DB 커넥션 풀 고갈 시작
14:00:25  첫 번째 Connection timeout 에러
14:00:30  Consumer 완전 중단
14:02:15  모니터링 알람 발생 (Consumer Lag > 10,000)
14:03:00  장애 인지 및 대응팀 소집
14:05:30  원인 파악 완료 (DB 커넥션 풀 고갈)
14:08:00  긴급 조치 시작 (커넥션 풀 증설)
14:08:45  애플리케이션 재시작
14:09:15  Consumer 정상화 시작
14:15:32  Consumer Lag 0 도달 (복구 완료)
14:20:00  정합성 검증 완료
```

### 2.2 상세 타임라인

#### Phase 1: 장애 발생 (14:00:00 - 14:00:30)

**14:00:00** - 선착순 이벤트 시작
```
초기 상태:
- VU: 10명
- Kafka Producer: 초당 20건 발행
- Consumer Lag: 0
- DB Connection Pool: 2/10 (20% 사용)
```

**14:00:05** - 트래픽 급증 (Spike!)
```
10초 만에 VU 1000명으로 급증
- Kafka Producer: 초당 3,200건 발행
- Consumer 처리 속도: 초당 150건
- Consumer Lag 시작: 0 → 500 → 1,500
```

**14:00:12** - Consumer Lag 1,000 돌파
```
Consumer 처리:
- DB INSERT 평균 시간: 50ms
- 동시 처리 Consumer 스레드: 3개 (파티션 수)
- 이론적 처리 능력: 초당 60건 (3 / 0.05)
- 실제 처리 속도: 초당 150건 (커넥션 풀 여유 있음)
```

**14:00:23** - DB 커넥션 풀 고갈 시작
```
HikariCP 상태:
- Active Connections: 10/10 (100%)
- Idle Connections: 0/10
- Threads awaiting connection: 15+
- Queue Size: 50+ (계속 증가 중)
```

**14:00:25** - 첫 번째 Connection timeout 에러
```log
2025-01-15 14:00:25.123 ERROR [coupon-db-sync-0]
c.z.h.p.HikariPool : HikariPool-1 - Connection is not available,
request timed out after 30000ms.

org.springframework.dao.DataAccessResourceFailureException:
Unable to acquire JDBC Connection; nested exception is
java.sql.SQLTransientConnectionException: HikariPool-1 -
Connection is not available, request timed out after 30000ms.
```

**14:00:30** - Consumer 완전 중단
```
모든 Consumer 스레드가 커넥션 대기 상태:
- Consumer Thread 1: WAITING for connection (30초 경과)
- Consumer Thread 2: WAITING for connection (28초 경과)
- Consumer Thread 3: WAITING for connection (25초 경과)

DB 저장: 완전 중단
Consumer Lag: 5,234 messages (계속 증가 중)
```

#### Phase 2: 장애 인지 및 원인 파악 (14:02:15 - 14:05:30)

**14:02:15** - 모니터링 알람 발생
```
Slack 알람:
🚨 [CRITICAL] Kafka Consumer Lag Alert
- Topic: coupon-issued
- Consumer Group: coupon-db-sync
- Current Lag: 12,543 messages
- Threshold: 1,000 messages
- Action Required: IMMEDIATE
```

**14:03:00** - 대응팀 소집 및 초기 조사
```bash
# 1. Consumer Lag 확인
$ kafka-consumer-groups.sh --bootstrap-server localhost:29092 \
  --describe --group coupon-db-sync

GROUP           TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
coupon-db-sync  coupon-issued  0          128             15234           15106
coupon-db-sync  coupon-issued  1          0               18567           18567
coupon-db-sync  coupon-issued  2          0               18540           18540

Total Lag: 52,213 messages
```

**14:03:45** - 애플리케이션 로그 확인
```bash
$ tail -f logs/application.log | grep ERROR

# 반복되는 에러 패턴 발견
Connection is not available, request timed out after 30000ms
Connection is not available, request timed out after 30000ms
Connection is not available, request timed out after 30000ms
... (초당 3개씩 발생)
```

**14:04:30** - DB 커넥션 상태 확인
```bash
# HikariCP 메트릭 확인 (JMX)
$ jconsole (HikariCP MBean 조회)

HikariPool-1:
- Total Connections: 10
- Active Connections: 10
- Idle Connections: 0
- Threads Awaiting Connection: 87
- Connection Timeout: 30000ms
```

**14:05:30** - 근본 원인 파악 완료
```
원인 분석:
1. DB 커넥션 풀 크기: 10개 (너무 작음)
2. Consumer 처리 속도: 초당 150건 가능
3. Producer 발행 속도: 초당 3,200건
4. 처리 속도 차이: 21배 차이 (3200/150)
5. 커넥션 풀 고갈: 10개 모두 사용 중 → 대기 → Timeout

결론: DB 커넥션 풀 크기 부족으로 Consumer 처리 중단
```

#### Phase 3: 긴급 조치 (14:08:00 - 14:09:15)

**14:08:00** - 긴급 조치 시작

**조치 1: application.yml 수정**
```yaml
# Before (기존)
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 30000

# After (긴급 조치)
spring:
  datasource:
    hikari:
      maximum-pool-size: 50      # 10 → 50 증가
      minimum-idle: 10
      connection-timeout: 20000   # 30초 → 20초 단축
      max-lifetime: 1800000       # 30분
      idle-timeout: 600000        # 10분
```

**조치 2: Consumer Concurrency 증가**
```yaml
# KafkaConsumerConfig
spring:
  kafka:
    listener:
      concurrency: 3  # 파티션 수와 동일 유지
```

**14:08:45** - 애플리케이션 재시작
```bash
# 1. Graceful Shutdown (기존 처리 완료 대기)
$ kill -15 <pid>

# 2. 재시작
$ ./gradlew bootRun

# 3. Health Check
$ curl http://localhost:8080/actuator/health
{"status":"UP"}
```

**14:09:15** - Consumer 정상화 확인
```
Consumer 재시작 후:
- Active Connections: 15/50 (30%)
- Idle Connections: 35/50 (70%)
- Consumer Lag: 52,213 → 처리 시작
- 처리 속도: 초당 약 400건 (커넥션 풀 여유 생김)
```

#### Phase 4: 복구 및 검증 (14:09:15 - 14:20:00)

**14:09:15 - 14:15:32** - Consumer Lag 처리
```
시간별 Lag 감소:
14:09:15 - Lag: 52,213
14:10:00 - Lag: 44,000 (45초간 8,213건 처리, 초당 182건)
14:11:00 - Lag: 20,500 (1분간 23,500건 처리, 초당 391건)
14:13:00 - Lag: 5,200  (2분간 15,300건 처리, 초당 127건)
14:15:32 - Lag: 0      (복구 완료!)

총 처리 시간: 6분 17초
평균 처리 속도: 초당 138건
```

**14:15:32** - Consumer Lag 0 도달
```bash
$ kafka-consumer-groups.sh --bootstrap-server localhost:29092 \
  --describe --group coupon-db-sync

GROUP           TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
coupon-db-sync  coupon-issued  0          15234           15234           0
coupon-db-sync  coupon-issued  1          18567           18567           0
coupon-db-sync  coupon-issued  2          18540           18540           0

✅ Total Lag: 0 messages
```

**14:16:00 - 14:20:00** - 데이터 정합성 검증
```bash
# 1. Redis 재고 확인
$ redis-cli GET "coupon:stock:1"
0  # 1,000개 모두 발급됨

# 2. Redis 발급자 수 확인
$ redis-cli SCARD "coupon:issued:1"
1000  # 1,000명 발급받음

# 3. DB 저장 건수 확인
$ mysql -e "SELECT COUNT(*) FROM user_coupon WHERE coupon_id = 1;"
1000  # ✅ 정합성 일치!

# 4. 중복 발급 확인
$ mysql -e "SELECT user_id, COUNT(*) FROM user_coupon
  WHERE coupon_id = 1 GROUP BY user_id HAVING COUNT(*) > 1;"
Empty set  # ✅ 중복 없음
```

**14:20:00** - 복구 완료 및 정상화 선언
```
최종 검증 결과:
✅ Consumer Lag: 0
✅ DB 정합성: 100% (1,000/1,000)
✅ 중복 발급: 0건
✅ 커넥션 풀 상태: 정상 (15/50, 30%)
✅ 서비스 정상화: 완료
```

---

## 3. 장애 감지 및 초기 대응

### 3.1 장애 감지 방법

#### 1) 모니터링 알람 (자동)
```
❌ 현재 상황: Kafka Consumer Lag 알람만 존재
- Threshold: 1,000 messages
- 실제 알람 발생: 14:02:15 (장애 발생 2분 15초 후)
- 문제점: 너무 늦게 감지됨

✅ 개선 필요: DB 커넥션 풀 모니터링 추가
```

#### 2) 모니터링 지표

**감지 가능한 지표:**

| 지표 | 정상 범위 | 임계값 | 장애 시 값 | 감지 시간 |
|------|----------|--------|-----------|----------|
| **Consumer Lag** | 0-100 | 1,000 | 52,213 | 2분 후 |
| **DB Connection Active** | 0-5 | 8 | 10/10 | ❌ 미감지 |
| **Connection Wait Time** | 0ms | 1000ms | 30000ms | ❌ 미감지 |
| **Consumer 처리 속도** | 100-200/s | 50/s | 0/s | ❌ 미감지 |

#### 3) 로그 패턴 (수동)
```log
# 반복되는 에러 패턴으로 감지 가능
Connection is not available, request timed out after 30000ms
Connection is not available, request timed out after 30000ms
Connection is not available, request timed out after 30000ms

# Grep으로 에러 카운트
$ grep "Connection is not available" logs/application.log | wc -l
2,341  # 2분간 2,341건 발생
```

### 3.2 초기 대응 절차

#### 1단계: 장애 상황 파악 (5분)
```bash
# 1. Consumer Lag 확인
kafka-consumer-groups.sh --describe --group coupon-db-sync

# 2. 애플리케이션 로그 확인
tail -f logs/application.log | grep ERROR

# 3. DB 커넥션 풀 상태 확인 (JMX)
jconsole (HikariCP MBean)

# 4. DB 서버 상태 확인
mysql -e "SHOW PROCESSLIST;"
mysql -e "SHOW STATUS LIKE 'Threads_connected';"
```

#### 2단계: 임시 조치 (3분)
```bash
# Option 1: Consumer 일시 중지 (Producer는 계속 동작)
# → Kafka에 메시지 쌓이지만 재시작 후 처리 가능
docker stop <consumer-container>

# Option 2: 커넥션 풀 동적 증설 (권장)
# → JMX로 runtime에 변경 가능 (재시작 불필요)
jconsole → HikariConfigMXBean → setMaximumPoolSize(50)
```

#### 3단계: 근본 원인 분석 (3분)
```
체크리스트:
□ DB 서버 다운? → mysql 접속 확인
□ DB 성능 저하? → SHOW PROCESSLIST 확인
□ Slow Query? → 쿼리 실행 시간 확인
□ 커넥션 풀 고갈? → HikariCP 메트릭 확인 ✅
□ 네트워크 문제? → ping, telnet 확인
```

#### 4단계: 긴급 조치 (5분)
```bash
# 1. application.yml 수정
vim src/main/resources/application.yml
# maximum-pool-size: 10 → 50

# 2. 애플리케이션 재시작
kill -15 <pid>
./gradlew bootRun

# 3. Health Check
curl http://localhost:8080/actuator/health
```

#### 5단계: 복구 검증 (10분)
```bash
# 1. Consumer Lag 감소 확인
watch -n 5 'kafka-consumer-groups.sh --describe --group coupon-db-sync'

# 2. DB 정합성 검증
mysql -e "SELECT COUNT(*) FROM user_coupon WHERE coupon_id = 1;"

# 3. 모니터링 지표 확인
# - Consumer Lag: 0
# - DB Connection: 정상
# - 에러 로그: 없음
```

---

## 4. 근본 원인 분석

### 4.1 직접 원인 (Direct Cause)

**DB 커넥션 풀 크기 부족**

```
설정값:
- maximum-pool-size: 10개

실제 필요량:
- Consumer 스레드: 3개 (파티션 수)
- 평균 처리 시간: 50ms
- 목표 처리 속도: 초당 400건
- 필요 커넥션: 400 * 0.05 = 20개

결과:
10개 < 20개 → 커넥션 부족 → 대기 → Timeout
```

### 4.2 근본 원인 (Root Cause)

#### 1) 설정 오류
```yaml
# 문제: 기본값 사용 (검증 없음)
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # HikariCP 기본값 그대로 사용
```

**원인:**
- 초기 개발 시 기본 설정 그대로 사용
- 부하 테스트에서 커넥션 풀 크기 검증 안 함
- 프로덕션 배포 전 성능 튜닝 누락

#### 2) 모니터링 부재
```
❌ DB 커넥션 풀 메트릭 미수집
❌ HikariCP MBean 모니터링 미설정
❌ Connection timeout 알람 없음
```

**결과:**
- 장애 발생 2분 15초 후 감지
- Consumer Lag 알람만 존재 → 너무 늦음

#### 3) 부하 테스트 부족
```
수행한 테스트:
✅ Heavy Spike Test (VU 1000)
  → 하지만 커넥션 풀은 확인 안 함

수행하지 않은 검증:
❌ DB 커넥션 풀 사용량 모니터링
❌ 장시간 부하 시 커넥션 상태 확인
❌ Consumer Lag 발생 시 시나리오
```

### 4.3 기술적 분석

#### HikariCP 동작 원리
```
1. Consumer가 DB INSERT 요청
   ↓
2. HikariCP에 커넥션 요청
   ↓
3-1. 사용 가능한 커넥션 있음 → 즉시 반환
3-2. 사용 가능한 커넥션 없음 → 대기 (connection-timeout)
   ↓
4. 30초 대기 후에도 커넥션 없음 → TimeoutException
   ↓
5. Consumer 처리 실패 → 메시지 재처리 (재시도)
   ↓
6. 재시도도 실패 → DLQ 이동 또는 Consumer 중단
```

#### 커넥션 풀 고갈 과정
```
시간    Producer  Consumer  Lag    Active  Idle   Status
                    처리속도        Conn    Conn
----------------------------------------------------------
14:00   20/s      150/s     0      2/10    8/10   정상
14:00   100/s     150/s     100    5/10    5/10   정상
14:01   3200/s    150/s     3,100  10/10   0/10   ⚠️ 고갈
14:01   3200/s    0/s       52,213 10/10   0/10   🔥 중단
```

#### Consumer Lag 증가 계산
```
Producer 발행 속도: 3,200건/초
Consumer 처리 속도: 150건/초
차이: 3,200 - 150 = 3,050건/초 증가

1분 후 Lag: 3,050 * 60 = 183,000건
실제 Lag: 52,213건 (Spike가 10초만 지속되고 감소했기 때문)
```

### 4.4 왜 기본값(10)이 문제였나?

**HikariCP 기본값 10개의 의미:**
- 일반적인 웹 애플리케이션 기준
- 동시 HTTP 요청 50-100개 처리 가능
- 평균 쿼리 실행 시간 10-20ms 가정

**우리 시스템의 특성:**
- Kafka Consumer: 초당 수천 건 처리 필요
- 배치 INSERT 없음 (1건씩 처리)
- 피크 시간대 트래픽: 평소의 100배
- 필요 커넥션: 최소 20-30개

**결론:**
```
일반 웹 애플리케이션 != Kafka Consumer 애플리케이션
→ 기본값 그대로 사용하면 안 됨
→ 부하 테스트로 적정값 도출 필요
```

---

## 5. 복구 절차

### 5.1 긴급 복구 (Emergency Recovery)

#### Step 1: 설정 파일 수정
```yaml
# src/main/resources/application.yml

spring:
  datasource:
    hikari:
      # 커넥션 풀 크기 증설
      maximum-pool-size: 50        # 10 → 50
      minimum-idle: 10              # 최소 유휴 커넥션

      # 타임아웃 조정
      connection-timeout: 20000     # 30초 → 20초
      validation-timeout: 5000      # 검증 타임아웃

      # 커넥션 라이프사이클
      max-lifetime: 1800000         # 30분 (커넥션 최대 수명)
      idle-timeout: 600000          # 10분 (유휴 커넥션 타임아웃)

      # 헬스 체크
      connection-test-query: SELECT 1
```

**설정값 근거:**
```
maximum-pool-size: 50
- Consumer 스레드: 3개
- 처리 속도 목표: 초당 500건
- 평균 처리 시간: 50ms
- 필요 커넥션: 500 * 0.05 = 25개
- 여유율 2배: 25 * 2 = 50개

minimum-idle: 10
- 평시 처리: 초당 100건
- 필요 커넥션: 100 * 0.05 = 5개
- 여유율 2배: 10개
```

#### Step 2: 애플리케이션 재시작
```bash
# 1. 현재 프로세스 확인
$ ps aux | grep java
ubuntu   12345  ...  java -jar ecommerce.jar

# 2. Graceful Shutdown (SIGTERM)
$ kill -15 12345

# 3. 프로세스 종료 대기 (최대 30초)
$ while kill -0 12345 2>/dev/null; do
    sleep 1
    echo "Waiting for graceful shutdown..."
  done

# 4. 재시작
$ nohup java -jar ecommerce.jar > app.log 2>&1 &

# 5. Health Check (30초 대기)
$ for i in {1..30}; do
    if curl -f http://localhost:8080/actuator/health; then
      echo "✅ Application is UP"
      break
    fi
    sleep 1
  done
```

#### Step 3: 복구 확인
```bash
# 1. Consumer 시작 확인
$ kafka-consumer-groups.sh --bootstrap-server localhost:29092 \
  --describe --group coupon-db-sync

# 2. DB 커넥션 풀 상태 확인
$ jconsole
# HikariPool-1 → Active Connections: 15/50 (정상)

# 3. 로그 확인
$ tail -f logs/application.log
# ✅ 에러 없음, INFO 레벨만 출력

# 4. Consumer Lag 감소 확인 (실시간)
$ watch -n 5 'kafka-consumer-groups.sh --bootstrap-server localhost:29092 \
  --describe --group coupon-db-sync | grep -v GROUP'
```

### 5.2 데이터 정합성 검증

#### Step 1: Redis vs DB 비교
```bash
# 1. Redis 발급 수 확인
REDIS_ISSUED=$(redis-cli SCARD "coupon:issued:1")
echo "Redis 발급자 수: $REDIS_ISSUED"

# 2. DB 발급 수 확인
DB_ISSUED=$(mysql -N -e "SELECT COUNT(*) FROM user_coupon WHERE coupon_id = 1;")
echo "DB 발급 수: $DB_ISSUED"

# 3. 비교
if [ "$REDIS_ISSUED" -eq "$DB_ISSUED" ]; then
  echo "✅ 정합성 일치: $REDIS_ISSUED == $DB_ISSUED"
else
  echo "❌ 정합성 불일치: Redis($REDIS_ISSUED) != DB($DB_ISSUED)"
  echo "차이: $((REDIS_ISSUED - DB_ISSUED))건"
fi
```

#### Step 2: 중복 발급 확인
```sql
-- 중복 발급자 조회
SELECT user_id, COUNT(*) as count
FROM user_coupon
WHERE coupon_id = 1
GROUP BY user_id
HAVING count > 1;

-- 결과
Empty set  -- ✅ 중복 없음
```

#### Step 3: 재고 정확성 확인
```bash
# 1. Redis 재고 확인
STOCK=$(redis-cli GET "coupon:stock:1")
echo "남은 재고: $STOCK"

# 2. 계산
INITIAL_STOCK=1000
ISSUED_COUNT=$(redis-cli SCARD "coupon:issued:1")
EXPECTED_STOCK=$((INITIAL_STOCK - ISSUED_COUNT))

if [ "$STOCK" -eq "$EXPECTED_STOCK" ]; then
  echo "✅ 재고 정확: $STOCK == $EXPECTED_STOCK"
else
  echo "❌ 재고 오류: $STOCK != $EXPECTED_STOCK"
fi
```

#### Step 4: 발급 순위 확인
```bash
# Top 10 발급자 조회 (순위 확인)
redis-cli ZRANGE "coupon:issue:log:1" 0 9 WITHSCORES

# 결과 예시
1) "user:123"    # userId
2) "1"           # 순위 (1등)
3) "user:456"
4) "2"
5) "user:789"
6) "3"
...
```

### 5.3 복구 완료 기준

**모든 항목이 ✅ 상태여야 복구 완료:**

| 항목 | 확인 방법 | 기준 | 상태 |
|------|----------|------|------|
| **Consumer Lag** | kafka-consumer-groups | 0 messages | ✅ 0 |
| **DB 정합성** | Redis vs DB 비교 | 일치 | ✅ 1000 == 1000 |
| **중복 발급** | SQL 쿼리 | 0건 | ✅ 0건 |
| **재고 정확성** | Redis 계산 | 일치 | ✅ 0 == 0 |
| **커넥션 풀** | HikariCP 메트릭 | < 80% | ✅ 15/50 (30%) |
| **에러 로그** | tail logs | 에러 없음 | ✅ 에러 없음 |
| **서비스 정상** | Health Check | UP | ✅ UP |

---

## 6. 재발 방지 대책

### 6.1 즉시 조치 (단기)

#### 1) 설정 최적화
```yaml
# application.yml (프로덕션)
spring:
  datasource:
    hikari:
      # 커넥션 풀 크기 (부하 테스트 기반)
      maximum-pool-size: 50
      minimum-idle: 10

      # 타임아웃 설정
      connection-timeout: 20000      # 20초
      validation-timeout: 5000       # 5초

      # 커넥션 라이프사이클
      max-lifetime: 1800000          # 30분
      idle-timeout: 600000           # 10분

      # 누수 감지
      leak-detection-threshold: 60000  # 1분

      # 헬스 체크
      connection-test-query: SELECT 1

      # 메트릭 활성화
      register-mbeans: true
```

#### 2) 모니터링 추가
```yaml
# application.yml - Actuator 설정
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ecommerce
```

**수집할 메트릭:**
- `hikaricp.connections.active` (활성 커넥션)
- `hikaricp.connections.idle` (유휴 커넥션)
- `hikaricp.connections.pending` (대기 중 스레드)
- `hikaricp.connections.timeout` (타임아웃 발생 수)
- `hikaricp.connections.usage` (사용률)

#### 3) 알람 설정
```yaml
# Prometheus Alert Rules
groups:
  - name: db_connection_pool
    interval: 10s
    rules:
      # 커넥션 풀 사용률 80% 이상
      - alert: HighDBConnectionUsage
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
        for: 1m
        annotations:
          summary: "DB 커넥션 풀 사용률 높음"
          description: "{{ $value }}% 사용 중"
        labels:
          severity: warning

      # 커넥션 타임아웃 발생
      - alert: DBConnectionTimeout
        expr: increase(hikaricp_connections_timeout_total[1m]) > 0
        annotations:
          summary: "DB 커넥션 타임아웃 발생"
          description: "{{ $value }}건 타임아웃"
        labels:
          severity: critical

      # 대기 중인 스레드 10개 이상
      - alert: DBConnectionPending
        expr: hikaricp_connections_pending > 10
        for: 30s
        annotations:
          summary: "DB 커넥션 대기 중"
          description: "{{ $value }}개 스레드 대기"
        labels:
          severity: warning
```

### 6.2 중기 대책 (1-2주)

#### 1) Consumer 성능 최적화

**배치 처리 도입:**
```java
@Service
public class CouponDatabaseSyncConsumer {

    private final List<CouponIssuedEvent> buffer =
        new CopyOnWriteArrayList<>();
    private final int BATCH_SIZE = 100;

    @KafkaListener(topics = "coupon-issued")
    public void consume(CouponIssuedEvent event) {
        buffer.add(event);

        if (buffer.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }

    @Scheduled(fixedDelay = 1000)  // 1초마다 강제 flush
    public void flushBatch() {
        if (buffer.isEmpty()) return;

        List<CouponIssuedEvent> batch = new ArrayList<>(buffer);
        buffer.clear();

        // 배치 INSERT
        userCouponRepository.saveAll(
            batch.stream()
                .map(this::toUserCoupon)
                .collect(Collectors.toList())
        );
    }
}
```

**효과:**
```
Before (1건씩):
- INSERT 100건 = 커넥션 100번 사용
- 처리 시간: 100 * 50ms = 5초

After (배치):
- INSERT 100건 = 커넥션 1번 사용
- 처리 시간: 1 * 200ms = 0.2초 (25배 빠름)

커넥션 사용량: 100개 → 1개 (100배 감소)
```

#### 2) Connection Pool 동적 조정
```java
@Component
public class DynamicHikariConfig {

    @Autowired
    private HikariDataSource dataSource;

    @Scheduled(fixedRate = 60000)  // 1분마다
    public void adjustPoolSize() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();

        int active = pool.getActiveConnections();
        int total = pool.getTotalConnections();
        double usage = (double) active / total;

        if (usage > 0.9) {
            // 사용률 90% 이상 → 증설
            int newSize = Math.min(total + 10, 100);
            dataSource.setMaximumPoolSize(newSize);
            log.warn("커넥션 풀 증설: {} → {}", total, newSize);
        } else if (usage < 0.3 && total > 20) {
            // 사용률 30% 미만 → 감축
            int newSize = Math.max(total - 10, 20);
            dataSource.setMaximumPoolSize(newSize);
            log.info("커넥션 풀 감축: {} → {}", total, newSize);
        }
    }
}
```

#### 3) Circuit Breaker 적용
```java
@Service
public class CouponDatabaseSyncConsumer {

    private final CircuitBreaker circuitBreaker =
        CircuitBreaker.ofDefaults("dbSync");

    @KafkaListener(topics = "coupon-issued")
    public void consume(CouponIssuedEvent event) {
        Try.ofSupplier(
            CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> saveToDatabase(event)
            )
        ).onFailure(throwable -> {
            if (throwable instanceof CircuitBreakerOpenException) {
                // Circuit Open → DLQ로 전송
                sendToDLQ(event);
            }
        });
    }
}
```

### 6.3 장기 대책 (1개월)

#### 1) Read Replica 도입
```yaml
# application.yml - Master/Slave 분리
spring:
  datasource:
    master:
      hikari:
        jdbc-url: jdbc:mysql://master-db:3306/ecommerce
        maximum-pool-size: 50

    slave:
      hikari:
        jdbc-url: jdbc:mysql://slave-db:3306/ecommerce
        maximum-pool-size: 30
        read-only: true
```

```java
// 쓰기는 Master, 읽기는 Slave
@Transactional
public void save(UserCoupon coupon) {
    masterRepository.save(coupon);  // Master
}

@Transactional(readOnly = true)
public UserCoupon findById(Long id) {
    return slaveRepository.findById(id);  // Slave
}
```

#### 2) DB Sharding (장기)
```
현재: 단일 DB
- 모든 쓰기가 1개 DB로 집중
- 커넥션 풀 한계 존재

Sharding 후: 쿠폰 ID 기반 분할
- coupon_id % 4 = 0 → DB1
- coupon_id % 4 = 1 → DB2
- coupon_id % 4 = 2 → DB3
- coupon_id % 4 = 3 → DB4

효과:
- 부하 분산: 1/4로 감소
- 커넥션 풀: 4배 증가 (4 * 50 = 200개)
```

#### 3) Kafka Partition 증가
```bash
# 현재: 3개 파티션
kafka-topics.sh --alter --topic coupon-issued \
  --partitions 9  # 3 → 9

# Consumer 인스턴스 증가 (3개 → 9개)
# → 병렬 처리 능력 3배 증가
```

### 6.4 프로세스 개선

#### 1) 부하 테스트 체크리스트
```markdown
## 부하 테스트 필수 확인 항목

### DB 관련
- [ ] 커넥션 풀 사용률 모니터링
- [ ] 커넥션 타임아웃 발생 확인
- [ ] Slow Query 탐색
- [ ] 트랜잭션 격리 수준 검증

### Kafka 관련
- [ ] Consumer Lag 추이 확인
- [ ] Producer 처리량 vs Consumer 처리량 비교
- [ ] Rebalancing 영향 확인

### 애플리케이션 관련
- [ ] JVM Heap 사용률
- [ ] GC 빈도 및 Pause 시간
- [ ] Thread Pool 사용률
- [ ] CPU/메모리 사용률
```

#### 2) 배포 전 검증 절차
```bash
# 1. 로컬 부하 테스트
k6 run k6/peak-test/spike-heavy.js

# 2. 스테이징 환경 검증
# - 프로덕션과 동일한 설정
# - 실제 트래픽의 50% 수준 테스트

# 3. 카나리 배포
# - 트래픽의 10% → 신규 버전
# - 1시간 모니터링
# - 정상 → 50% → 100%

# 4. 롤백 준비
# - 이전 버전 이미지 보관
# - 롤백 스크립트 준비
```

#### 3) 장애 대응 훈련 (매월)
```
시나리오 기반 훈련:
1. DB 커넥션 풀 고갈
2. Kafka Consumer Lag 급증
3. Redis 메모리 부족
4. 애플리케이션 OOM

훈련 내용:
- 장애 감지 (5분 이내)
- 원인 파악 (10분 이내)
- 긴급 조치 (5분 이내)
- 복구 검증 (10분 이내)

목표: 총 30분 이내 복구
```

---

## 부록

### A. 관련 로그

#### Consumer 에러 로그
```log
2025-01-15 14:00:25.123 ERROR [coupon-db-sync-0] c.z.h.p.HikariPool :
HikariPool-1 - Connection is not available, request timed out after 30000ms.

2025-01-15 14:00:25.456 ERROR [coupon-db-sync-1] o.s.k.l.KafkaMessageListenerContainer :
Consumer exception
org.springframework.dao.DataAccessResourceFailureException:
Unable to acquire JDBC Connection; nested exception is
java.sql.SQLTransientConnectionException: HikariPool-1 -
Connection is not available, request timed out after 30000ms.
	at org.springframework.jdbc.datasource.DataSourceUtils.getConnection(DataSourceUtils.java:82)
	at org.springframework.jdbc.core.JdbcTemplate.execute(JdbcTemplate.java:376)
	...
```

#### HikariCP 상태 로그
```log
2025-01-15 14:00:30.789 WARN [hikari-housekeeper] c.z.h.p.HikariPool :
HikariPool-1 - Thread starvation or clock leap detected
(housekeeper delta=32s123ms).

2025-01-15 14:01:00.123 ERROR [hikari-housekeeper] c.z.h.p.HikariPool :
HikariPool-1 - Connection leak detection triggered for connection
com.mysql.cj.jdbc.ConnectionImpl@7f3b84b8,
stack trace follows
```

### B. 모니터링 스크립트

#### 실시간 커넥션 모니터링
```bash
#!/bin/bash
# monitor-connections.sh

while true; do
  ACTIVE=$(jcmd <pid> GC.heap_info | grep "hikaricp.connections.active" | awk '{print $2}')
  TOTAL=$(jcmd <pid> GC.heap_info | grep "hikaricp.connections.max" | awk '{print $2}')
  USAGE=$(echo "scale=2; $ACTIVE / $TOTAL * 100" | bc)

  echo "[$(date +%H:%M:%S)] Active: $ACTIVE/$TOTAL ($USAGE%)"

  if (( $(echo "$USAGE > 80" | bc -l) )); then
    echo "⚠️  WARNING: High connection usage!"
  fi

  sleep 5
done
```

#### Consumer Lag 모니터링
```bash
#!/bin/bash
# monitor-lag.sh

while true; do
  LAG=$(kafka-consumer-groups.sh --bootstrap-server localhost:29092 \
    --describe --group coupon-db-sync | \
    awk 'NR>1 {sum+=$6} END {print sum}')

  echo "[$(date +%H:%M:%S)] Consumer Lag: $LAG"

  if [ "$LAG" -gt 1000 ]; then
    echo "🚨 CRITICAL: Consumer Lag > 1000!"
  fi

  sleep 10
done
```

### C. 체크리스트

#### 장애 대응 체크리스트
```markdown
## 초기 대응 (5분)
- [ ] Slack 알람 확인
- [ ] 장애 상황 파악 (Consumer Lag, 에러 로그)
- [ ] 영향 범위 확인 (사용자 영향도)
- [ ] 대응팀 소집

## 원인 분석 (5분)
- [ ] DB 서버 상태 확인
- [ ] 커넥션 풀 상태 확인
- [ ] Consumer 상태 확인
- [ ] 네트워크 상태 확인

## 긴급 조치 (5분)
- [ ] application.yml 수정
- [ ] 애플리케이션 재시작
- [ ] Health Check 확인
- [ ] Consumer 정상화 확인

## 복구 검증 (10분)
- [ ] Consumer Lag = 0 확인
- [ ] DB 정합성 검증
- [ ] 중복 발급 확인
- [ ] 모니터링 지표 정상 확인

## 사후 조치
- [ ] 장애 보고서 작성
- [ ] 재발 방지 대책 수립
- [ ] 모니터링 강화
- [ ] 관련자 공유
```