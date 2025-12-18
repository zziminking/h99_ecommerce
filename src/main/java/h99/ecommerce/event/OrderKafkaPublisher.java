package h99.ecommerce.event;

import h99.ecommerce.kafka.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트를 Kafka로 발행하는 중간 리스너
 * 트랜잭션 커밋 후 Kafka로 이벤트를 전달하여 데이터 일관성 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaPublisher {

    private final OrderEventProducer orderEventProducer;

    /**
     * 트랜잭션 커밋 후 Kafka로 이벤트 발행
     */
    @Async("orderEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishToKafka(OrderCompletedEvent event) {
        try {
            orderEventProducer.publishOrderCompleted(event);

        } catch (Exception e) {
            log.error("[Kafka Publisher] Kafka 발행 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
            // TODO: 실패 시 재시도 로직 또는 Dead Letter Queue 처리
        }
    }
}
