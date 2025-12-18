package h99.ecommerce.kafka;

import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 이벤트 Kafka Producer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 쿠폰 발급 완료 이벤트를 Kafka로 발행
     */
    public void publishCouponIssued(CouponIssuedEvent event) {
        String key = event.getCouponId().toString();

        try {
            var result = kafkaTemplate.send(KafkaConfig.COUPON_ISSUED_TOPIC, key, event).get(5, TimeUnit.SECONDS);

            RecordMetadata metadata = result.getRecordMetadata();
            log.info("[Kafka Producer] 전송 완료 - topic: {}, partition: {}, offset: {}, requestId: {}",
                metadata.topic(), metadata.partition(), metadata.offset(), event.getRequestId());

        } catch (Exception e) {
            log.error("[Kafka Producer] 전송 실패 - requestId: {}, couponId: {}, error: {}",
                event.getRequestId(), event.getCouponId(), e.getMessage(), e);
            throw new RuntimeException("Kafka 이벤트 발행 실패", e);
        }
    }
}