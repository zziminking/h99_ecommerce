package h99.ecommerce.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponRepository;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.user.UserRepository;
import h99.ecommerce.event.CouponIssuedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 정보를 RDB에 동기화하는 Consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponDatabaseSyncConsumer {

    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUED_TOPIC,
        groupId = "coupon-db-sync-group",
        containerFactory = "couponEventListenerContainerFactory"
    )
    public void syncToDatabase(
        @Payload CouponIssuedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[DB Sync] 쿠폰 발급 이벤트 수신 - requestId: {}, userId: {}, couponId: {}, topic: {}, partition: {}, offset: {}",
            event.getRequestId(), event.getUserId(), event.getCouponId(), topic, partition, offset);

        try {
            User user = userRepository.findOne(event.getUserId());
            if (user == null) {
                throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + event.getUserId());
            }

            Coupon coupon = couponRepository.findOne(event.getCouponId());
            if (coupon == null) {
                throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + event.getCouponId());
            }

            // 중복 발급 방지
            if (userCouponRepository.findByUserIdAndCouponId(event.getUserId(), event.getCouponId()).isPresent()) {
                log.warn("[DB Sync] 이미 발급된 쿠폰 - userId: {}, couponId: {}", event.getUserId(), event.getCouponId());
                updateRedisStatus(event.getRequestId(), "COMPLETED", "이미 발급된 쿠폰입니다.");
                return;
            }

            LocalDateTime issuedAt = event.getIssuedAt() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getIssuedAt()), ZoneId.systemDefault())
                : LocalDateTime.now();

            UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .isUsed(false)
                .createdAt(issuedAt)
                .build();

            userCouponRepository.save(userCoupon);

            updateRedisStatus(event.getRequestId(), "COMPLETED", "쿠폰 발급이 완료되었습니다.");

            log.info("[DB Sync] 쿠폰 발급 완료 - requestId: {}, userCouponId: {}, userId: {}, couponId: {}",
                event.getRequestId(), userCoupon.getUserCouponId(), event.getUserId(), event.getCouponId());

        } catch (Exception e) {
            log.error("[DB Sync] 쿠폰 발급 실패 - requestId: {}, error: {}",
                event.getRequestId(), e.getMessage(), e);

            updateRedisStatus(event.getRequestId(), "FAILED", "쿠폰 발급 처리 중 오류가 발생했습니다: " + e.getMessage());

            throw e;
        }
    }

    private void updateRedisStatus(String requestId, String status, String message) {
        try {
            String statusKey = "coupon:issue:status:" + requestId;
            Map<String, Object> statusData = Map.of(
                "status", status,
                "message", message,
                "updatedAt", System.currentTimeMillis()
            );
            redisTemplate.opsForValue().set(
                statusKey,
                objectMapper.writeValueAsString(statusData),
                10, TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("[DB Sync] Redis 상태 업데이트 실패 - requestId: {}, status: {}", requestId, status, e);
        }
    }
}