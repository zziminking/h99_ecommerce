package h99.ecommerce.kafka;

import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 주문 생성 이력 로깅 Consumer
 * Kafka에서 주문 완료 이벤트를 consume하여 이력을 로깅
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryLogger {

    /**
     * 주문 완료 이벤트 consume 및 이력 로깅
     */
    @KafkaListener(
        topics = KafkaConfig.ORDER_COMPLETED_TOPIC,
        groupId = "order-history-logger-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void logOrderHistory(
        @Payload OrderCompletedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        try {
            log.info("==============================================");
            log.info("[주문 이력 로깅] 이벤트 수신");
            log.info("- Kafka Partition: {}, Offset: {}", partition, offset);
            log.info("- 주문 ID: {}", event.getOrderId());
            log.info("- 사용자 ID: {}", event.getUserId());
            log.info("- 총 금액: {}원", event.getTotalPrice());
            log.info("- 총 수량: {}개", event.getTotalQuantity());
            log.info("==============================================");

            // TODO: 실제 주문 이력 저장 로직 구현
            // 예: OrderHistory 엔티티에 저장, 외부 로그 시스템에 전송 등
            saveOrderHistory(event);

        } catch (Exception e) {
            log.error("[주문 이력 로깅] 처리 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
            // TODO: 에러 핸들링 (재시도, Dead Letter Queue 등)
        }
    }

    /**
     * 주문 이력 저장
     */
    private void saveOrderHistory(OrderCompletedEvent event) {
        // 실제 DB 저장이나 외부 시스템 연동 로직
        log.debug("주문 이력 저장 완료 - orderId: {}", event.getOrderId());
    }
}
