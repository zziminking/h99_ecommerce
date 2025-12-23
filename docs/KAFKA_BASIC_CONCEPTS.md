# Kafka 기본 개념 학습 문서

## 목차
1. [Kafka란?](#1-kafka란)
2. [핵심 개념](#2-핵심-개념)
3. [Producer (생산자)](#3-producer-생산자)
4. [Consumer (소비자)](#4-consumer-소비자)
5. [프로젝트 구현 예시](#5-프로젝트-구현-예시)

---

## 1. Kafka란?

Apache Kafka는 **분산 이벤트 스트리밍 플랫폼**으로, 대용량의 실시간 데이터를 처리하기 위해 설계된 메시징 시스템입니다.

### 주요 특징
- **높은 처리량**: 초당 수백만 건의 메시지 처리 가능
- **확장성**: 수평적 확장(Scale-out)이 용이
- **내구성**: 디스크에 메시지를 저장하여 데이터 손실 방지
- **비동기 처리**: Producer와 Consumer가 독립적으로 동작

### 사용 사례
- 마이크로서비스 간 이벤트 전달
- 실시간 로그 수집 및 분석
- 데이터 파이프라인 구축
- 시스템 간 비동기 통신

---

## 2. 핵심 개념

### 2.1 Topic (토픽)
- 메시지가 저장되는 **논리적인 채널**
- 데이터베이스의 테이블과 유사한 개념
- 예: `order-completed`

```java
// KafkaConfig.java:14
public static final String ORDER_COMPLETED_TOPIC = "order-completed";
```

### 2.2 Partition (파티션)
- 토픽을 여러 개로 나눈 **물리적인 저장 단위**
- 병렬 처리를 위해 사용
- 같은 키를 가진 메시지는 동일한 파티션으로 전송됨

```java
// KafkaConfig.java:21-26
@Bean
public NewTopic orderCompletedTopic() {
    return TopicBuilder.name(ORDER_COMPLETED_TOPIC)
        .partitions(3)  // 3개의 파티션으로 분할
        .replicas(1)
        .build();
}
```

### 2.3 Consumer Group (컨슈머 그룹)
- 동일한 토픽을 구독하는 **Consumer들의 논리적 그룹**
- 같은 그룹 내에서는 메시지를 **분산하여** 처리 (한 번만 처리)
- 다른 그룹 간에는 메시지를 **독립적으로** 처리 (각 그룹이 모두 처리)

### 2.4 Offset (오프셋)
- 파티션 내 메시지의 **고유 번호** (순번)
- Consumer가 어디까지 메시지를 읽었는지 추적하는 데 사용

---

## 3. Producer (생산자)

Producer는 Kafka 토픽에 **메시지를 발행(Publish)**하는 역할을 합니다.

### 3.1 Producer의 동작 흐름

```
[이벤트 발생]
    ↓
[OrderKafkaPublisher] - @TransactionalEventListener (트랜잭션 커밋 후)
    ↓
[OrderEventProducer] - KafkaTemplate.send()
    ↓
[Kafka Broker] - Topic의 특정 Partition에 저장
```

### 3.2 프로젝트 구현: OrderEventProducer

```java
// OrderEventProducer.java:24-37
public void publishOrderCompleted(OrderCompletedEvent event) {
    String key = event.getOrderId().toString();  // 메시지 키 (같은 주문은 같은 파티션으로)

    try {
        SendResult<String, OrderCompletedEvent> result =
            kafkaTemplate.send(KafkaConfig.ORDER_COMPLETED_TOPIC, key, event)
                .get(5, TimeUnit.SECONDS);  // 동기 방식 (5초 타임아웃)

    } catch (Exception e) {
        log.error("[Kafka] 주문 완료 이벤트 발행 실패 - orderId: {}",
            event.getOrderId(), e);
        throw new RuntimeException("Kafka 이벤트 발행 실패", e);
    }
}
```

### 3.3 주요 개념

#### Message Key
- 메시지의 식별자 역할
- **같은 키를 가진 메시지는 같은 파티션으로 전송됨** (순서 보장)
- 키가 없으면 라운드 로빈 방식으로 파티션 할당

### 3.4 OrderKafkaPublisher의 역할

```java
// OrderKafkaPublisher.java:25-30
@Async("orderEventExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publishToKafka(OrderCompletedEvent event) {
    try {
        orderEventProducer.publishOrderCompleted(event);
    } catch (Exception e) {
        log.error("[Kafka Publisher] Kafka 발행 실패 - orderId: {}",
            event.getOrderId(), e);
    }
}
```

**핵심 포인트:**
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
  - 데이터베이스 트랜잭션이 **완전히 커밋된 후**에만 Kafka로 메시지 발행
  - 데이터 일관성 보장 (DB 저장 실패 시 Kafka 메시지도 발행되지 않음)
- `@Async`: 비동기로 실행되어 메인 스레드를 블로킹하지 않음

---

## 4. Consumer (소비자)

Consumer는 Kafka 토픽의 **메시지를 구독(Subscribe)하고 처리**하는 역할을 합니다.

### 4.1 Consumer의 동작 흐름

```
[Kafka Broker - Topic: order-completed]
           ↓
    ┌──────┴──────┬──────────────┬─────────────┐
    ↓             ↓              ↓             ↓
[그룹 1]      [그룹 2]       [그룹 3]       [그룹 N]
외부 동기화    알림 발송      이력 로깅      기타 처리
```

### 4.2 프로젝트 구현: 3개의 Consumer

#### 1) OrderHistoryLogger - 주문 이력 로깅

```java
// OrderHistoryLogger.java:25-34
@KafkaListener(
    topics = KafkaConfig.ORDER_COMPLETED_TOPIC,
    groupId = "order-history-logger-group",  // 독립적인 Consumer Group
    containerFactory = "kafkaListenerContainerFactory"
)
public void logOrderHistory(
    @Payload OrderCompletedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset
) {
    log.info("[주문 이력 로깅] 파티션: {}, 오프셋: {}", partition, offset);
    log.info("주문 ID: {}, 사용자: {}, 금액: {}",
        event.getOrderId(), event.getUserId(), event.getTotalPrice());
    saveOrderHistory(event);
}
```

#### 2) OrderExternalSyncListener - 외부 시스템 동기화

```java
// OrderExternalSyncListener.java:24-33
@KafkaListener(
    topics = KafkaConfig.ORDER_COMPLETED_TOPIC,
    groupId = "order-external-sync-group",  // 독립적인 Consumer Group
    containerFactory = "kafkaListenerContainerFactory"
)
public void handleOrderCompleted(
    @Payload OrderCompletedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset
) {
    log.info("[외부 동기화] 주문 완료 이벤트 수신 - partition: {}, offset: {}",
        partition, offset);
    syncToExternalSystem(event);  // ERP, WMS 등 외부 시스템과 동기화
}
```

#### 3) OrderNotificationListener - 알림 발송

```java
// OrderNotificationListener.java:23-32
@KafkaListener(
    topics = KafkaConfig.ORDER_COMPLETED_TOPIC,
    groupId = "order-notification-group",  // 독립적인 Consumer Group
    containerFactory = "kafkaListenerContainerFactory"
)
public void handleOrderCompleted(
    @Payload OrderCompletedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset
) {
    log.info("[알림] 주문 완료 이벤트 수신 - partition: {}, offset: {}",
        partition, offset);
    sendOrderNotification(event);  // SMS, Email, Push 등
}
```

### 4.3 Consumer Group의 핵심 개념

#### 같은 토픽, 다른 Consumer Group
- 3개의 Consumer 모두 **동일한 토픽** (`order-completed`)을 구독
- **각각 다른 Consumer Group**을 사용하여 독립적으로 동작
- 하나의 메시지가 **모든 Consumer Group에 각각 전달됨**

#### 동작 예시

```
주문 완료 이벤트 발생 (orderId: 123)
         ↓
[order-completed Topic에 저장]
         ↓
    ┌────┴────┬─────────────┬────────────┐
    ↓         ↓             ↓            ↓
[그룹1]    [그룹2]      [그룹3]      [...]
이력로깅   외부동기화    알림발송    기타처리

→ 동일한 메시지를 각 그룹이 독립적으로 처리
```

### 4.4 Consumer 주요 어노테이션

| 어노테이션 | 설명 |
|-----------|------|
| `@KafkaListener` | Kafka 메시지를 수신하는 메서드 지정 |
| `@Payload` | 메시지 본문(데이터)을 파라미터로 받음 |
| `@Header(KafkaHeaders.RECEIVED_PARTITION)` | 메시지가 저장된 파티션 번호 |
| `@Header(KafkaHeaders.OFFSET)` | 파티션 내 메시지의 오프셋 |

---

## 5. 프로젝트 구현 예시

### 5.1 전체 흐름도

```
[1. 주문 생성 API 호출]
         ↓
[2. OrderService - 주문 처리 및 DB 저장]
         ↓
[3. 트랜잭션 커밋 완료]
         ↓
[4. OrderKafkaPublisher - 이벤트 수신] (@TransactionalEventListener)
         ↓
[5. OrderEventProducer - Kafka로 발행] (KafkaTemplate)
         ↓
[6. Kafka Broker - order-completed Topic에 저장]
         ↓
    ┌────┴────┬─────────────┬────────────┐
    ↓         ↓             ↓            ↓
[Consumer1] [Consumer2]  [Consumer3]  [...]
이력로깅    외부동기화   알림발송     기타처리
```
