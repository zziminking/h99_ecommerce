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
 * 주문 생성 리스너 (Kafka Consumer)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    /**
     * 주문 완료 알림 발송 (Kafka Consumer)
     */
    @KafkaListener(
        topics = KafkaConfig.ORDER_COMPLETED_TOPIC,
        groupId = "order-notification-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCompleted(
        @Payload OrderCompletedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        try {
            log.info("[알림] 주문 완료 이벤트 수신 - partition: {}, offset: {}", partition, offset);
            log.info("주문 알림 발송 시작 - orderId: {}, userId: {}",
                event.getOrderId(), event.getUserId());

            sendOrderNotification(event);

            log.info("주문 알림 발송 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("주문 알림 발송 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 주문 알림 발송
     */
    private void sendOrderNotification(OrderCompletedEvent event) {
        log.info("주문 알림 발송 - orderId: {}, userId: {}", event.getOrderId(), event.getUserId());
        // TODO: 실제 알림 발송 로직 구현 (SMS, Email, Push 등)
    }
}
