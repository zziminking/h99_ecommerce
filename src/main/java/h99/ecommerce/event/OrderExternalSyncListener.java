package h99.ecommerce.event;

import h99.ecommerce.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 주문 외부 시스템 동기화 리스너 (Kafka Consumer)
 * 주문 완료 시 외부 시스템과 동기화를 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExternalSyncListener {

    /**
     * 주문 완료 외부 시스템 동기화 (Kafka Consumer)
     */
    @KafkaListener(
        topics = KafkaConfig.ORDER_COMPLETED_TOPIC,
        groupId = "order-external-sync-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCompleted(
        @Payload OrderCompletedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        try {
            log.info("[외부 동기화] 주문 완료 이벤트 수신 - partition: {}, offset: {}", partition, offset);
            log.info("외부 시스템 동기화 시작 - orderId: {}, totalPrice: {}",
                event.getOrderId(), event.getTotalPrice());

            syncToExternalSystem(event);

            log.info("외부 시스템 동기화 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("외부 시스템 동기화 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 외부 시스템 동기화
     */
    private void syncToExternalSystem(OrderCompletedEvent event) {
        log.info("외부 시스템 동기화 - orderId: {}", event.getOrderId());
        // TODO: 실제 외부 시스템 동기화 로직 구현 (ERP, WMS 등)
    }
}