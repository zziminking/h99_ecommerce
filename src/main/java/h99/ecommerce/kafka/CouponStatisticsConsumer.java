package h99.ecommerce.kafka;

import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.event.CouponIssuedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 통계를 수집하는 Consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStatisticsConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH");

    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUED_TOPIC,
        groupId = "coupon-statistics-group",
        containerFactory = "couponEventListenerContainerFactory"
    )
    public void collectStatistics(
        @Payload CouponIssuedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[Statistics] 쿠폰 발급 이벤트 수신 - requestId: {}, userId: {}, couponId: {}, topic: {}, partition: {}, offset: {}",
            event.getRequestId(), event.getUserId(), event.getCouponId(), topic, partition, offset);

        try {
            LocalDateTime issuedAt = event.getIssuedAt() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getIssuedAt()), ZoneId.systemDefault())
                : LocalDateTime.now();

            String date = issuedAt.format(DATE_FORMATTER);
            String hour = issuedAt.format(HOUR_FORMATTER);

            // 전체 발급 수
            redisTemplate.opsForValue().increment("coupon:stats:total");

            // 쿠폰별 발급 수
            redisTemplate.opsForValue().increment("coupon:stats:coupon:" + event.getCouponId());

            // 일자별 발급 수
            redisTemplate.opsForValue().increment("coupon:stats:date:" + date);

            // 시간대별 발급 수
            redisTemplate.opsForValue().increment("coupon:stats:hour:" + hour);

            // 쿠폰별 일자별 발급 수
            redisTemplate.opsForValue().increment("coupon:stats:coupon:" + event.getCouponId() + ":date:" + date);

            log.info("[Statistics] 통계 수집 완료 - requestId: {}, couponId: {}, date: {}, hour: {}",
                event.getRequestId(), event.getCouponId(), date, hour);

        } catch (Exception e) {
            log.error("[Statistics] 통계 수집 실패 - requestId: {}, error: {}",
                event.getRequestId(), e.getMessage(), e);
            throw e;
        }
    }
}