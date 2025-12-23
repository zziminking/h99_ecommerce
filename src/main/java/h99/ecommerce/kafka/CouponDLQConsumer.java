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
import java.util.HashMap;
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
 * DLQ 메시지를 처리하는 Consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponDLQConsumer {

    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DLQErrorClassifier errorClassifier;

    @Transactional
    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUED_DLQ_TOPIC,
        groupId = "coupon-dlq-group",
        containerFactory = "couponEventListenerContainerFactory"
    )
    public void processDLQ(
        @Payload CouponIssuedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
        @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace
    ) {
        log.error("[DLQ] DLQ 메시지 수신 - requestId: {}, userId: {}, couponId: {}, topic: {}, partition: {}, offset: {}",
            event.getRequestId(), event.getUserId(), event.getCouponId(), topic, partition, offset);
        log.error("[DLQ] 에러 메시지: {}", exceptionMessage);

        try {
            // DLQ 이력 저장
            saveDLQHistory(event, exceptionMessage, stackTrace);

            // 에러 분류
            DLQErrorClassifier.ErrorType errorType = classifyErrorFromMessage(exceptionMessage);
            log.info("[DLQ] 에러 타입: {} - requestId: {}", errorType, event.getRequestId());

            // 재처리 시도
            if (errorType == DLQErrorClassifier.ErrorType.TRANSIENT) {
                log.info("[DLQ] 일시적 에러로 판단, 재처리 시도 - requestId: {}", event.getRequestId());
                retryProcessing(event);
            } else {
                log.warn("[DLQ] 영구적 에러로 판단, 보상 트랜잭션 실행 - requestId: {}", event.getRequestId());
                executeCompensation(event, exceptionMessage);
            }

        } catch (Exception e) {
            log.error("[DLQ] DLQ 처리 중 에러 발생 - requestId: {}, error: {}",
                event.getRequestId(), e.getMessage(), e);
            // DLQ 처리 실패는 로깅만 하고 계속 진행 (무한 루프 방지)
        }
    }

    /**
     * DLQ 이력 저장 (Redis)
     */
    private void saveDLQHistory(CouponIssuedEvent event, String errorMessage, String stackTrace) {
        try {
            String dlqKey = "coupon:dlq:" + event.getRequestId();
            Map<String, Object> dlqData = new HashMap<>();
            dlqData.put("requestId", event.getRequestId());
            dlqData.put("userId", event.getUserId());
            dlqData.put("couponId", event.getCouponId());
            dlqData.put("errorMessage", errorMessage);
            dlqData.put("stackTrace", stackTrace != null ? stackTrace.substring(0, Math.min(500, stackTrace.length())) : "");
            dlqData.put("timestamp", System.currentTimeMillis());
            dlqData.put("retryCount", getRetryCount(event.getRequestId()) + 1);

            redisTemplate.opsForValue().set(
                dlqKey,
                objectMapper.writeValueAsString(dlqData),
                7, TimeUnit.DAYS
            );

            log.info("[DLQ] DLQ 이력 저장 완료 - requestId: {}", event.getRequestId());
        } catch (Exception e) {
            log.error("[DLQ] DLQ 이력 저장 실패 - requestId: {}", event.getRequestId(), e);
        }
    }

    /**
     * 재처리 시도
     */
    private void retryProcessing(CouponIssuedEvent event) {
        try {
            User user = userRepository.findOne(event.getUserId());
            if (user == null) {
                throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + event.getUserId());
            }

            Coupon coupon = couponRepository.findOne(event.getCouponId());
            if (coupon == null) {
                throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + event.getCouponId());
            }

            // 중복 확인
            if (userCouponRepository.findByUserIdAndCouponId(event.getUserId(), event.getCouponId()).isPresent()) {
                log.warn("[DLQ] 이미 발급된 쿠폰 - userId: {}, couponId: {}", event.getUserId(), event.getCouponId());
                updateRedisStatus(event.getRequestId(), "COMPLETED", "재처리 완료 (이미 발급됨)");
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
            updateRedisStatus(event.getRequestId(), "COMPLETED", "재처리 성공");

            log.info("[DLQ] 재처리 성공 - requestId: {}, userCouponId: {}", event.getRequestId(), userCoupon.getUserCouponId());

        } catch (Exception e) {
            log.error("[DLQ] 재처리 실패 - requestId: {}, error: {}", event.getRequestId(), e.getMessage(), e);
            updateRedisStatus(event.getRequestId(), "FAILED", "재처리 실패: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 보상 트랜잭션 실행
     */
    private void executeCompensation(CouponIssuedEvent event, String errorMessage) {
        try {
            // Redis 상태를 FAILED로 업데이트
            updateRedisStatus(event.getRequestId(), "FAILED", "처리 불가: " + errorMessage);

            // TODO: 필요시 Redis 쿠폰 재고 복구
            // couponRedisRepository.incrementStock(event.getCouponId());
            // couponRedisRepository.removeIssuer(event.getCouponId(), event.getUserId());

            // TODO: 관리자 알림 발송
            log.error("[DLQ] 관리자 알림 필요 - requestId: {}, userId: {}, couponId: {}, error: {}",
                event.getRequestId(), event.getUserId(), event.getCouponId(), errorMessage);

        } catch (Exception e) {
            log.error("[DLQ] 보상 트랜잭션 실패 - requestId: {}", event.getRequestId(), e);
        }
    }

    /**
     * 에러 메시지에서 에러 타입 분류
     */
    private DLQErrorClassifier.ErrorType classifyErrorFromMessage(String errorMessage) {
        if (errorMessage == null) {
            return DLQErrorClassifier.ErrorType.UNKNOWN;
        }

        if (errorMessage.contains("timeout") || errorMessage.contains("Timeout")
            || errorMessage.contains("Lock") || errorMessage.contains("Connection")) {
            return DLQErrorClassifier.ErrorType.TRANSIENT;
        }

        if (errorMessage.contains("IllegalArgument") || errorMessage.contains("IllegalState")
            || errorMessage.contains("NullPointer") || errorMessage.contains("찾을 수 없습니다")) {
            return DLQErrorClassifier.ErrorType.PERMANENT;
        }

        return DLQErrorClassifier.ErrorType.UNKNOWN;
    }

    /**
     * 재시도 횟수 조회
     */
    private int getRetryCount(String requestId) {
        try {
            String dlqKey = "coupon:dlq:" + requestId;
            String data = redisTemplate.opsForValue().get(dlqKey);
            if (data != null) {
                Map<String, Object> dlqData = objectMapper.readValue(data, Map.class);
                return (Integer) dlqData.getOrDefault("retryCount", 0);
            }
        } catch (Exception e) {
            log.error("[DLQ] 재시도 횟수 조회 실패 - requestId: {}", requestId, e);
        }
        return 0;
    }

    /**
     * Redis 상태 업데이트
     */
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
            log.error("[DLQ] Redis 상태 업데이트 실패 - requestId: {}", requestId, e);
        }
    }
}