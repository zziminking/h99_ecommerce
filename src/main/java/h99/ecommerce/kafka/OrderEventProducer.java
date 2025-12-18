package h99.ecommerce.kafka;

import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 주문 이벤트 Kafka Producer
 * @Async로 호출되므로 동기 방식으로 Kafka 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

    public void publishOrderCompleted(OrderCompletedEvent event) {
        String key = event.getOrderId().toString();

        try {
            SendResult<String, OrderCompletedEvent> result =
                kafkaTemplate.send(KafkaConfig.ORDER_COMPLETED_TOPIC, key, event)
                    .get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("[Kafka] 주문 완료 이벤트 발행 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Kafka 이벤트 발행 실패", e);
        }
    }
}
