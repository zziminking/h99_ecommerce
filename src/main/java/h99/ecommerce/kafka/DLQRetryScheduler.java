package h99.ecommerce.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.event.CouponIssuedEvent;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DLQ 메시지 재처리 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DLQRetryScheduler {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final int MAX_RETRY_COUNT = 5;

    /**
     * 5분마다 DLQ 이력을 조회하여 재처리 가능한 메시지를 재전송
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5분마다, 1분 후 시작
    public void retryDLQMessages() {
        log.info("[DLQ Scheduler] DLQ 재처리 시작");

        try {
            Set<String> dlqKeys = redisTemplate.keys("coupon:dlq:*");
            if (dlqKeys == null || dlqKeys.isEmpty()) {
                log.info("[DLQ Scheduler] 재처리할 DLQ 메시지 없음");
                return;
            }

            int retryCount = 0;
            int skipCount = 0;

            for (String dlqKey : dlqKeys) {
                try {
                    String data = redisTemplate.opsForValue().get(dlqKey);
                    if (data == null) {
                        continue;
                    }

                    Map<String, Object> dlqData = objectMapper.readValue(data, Map.class);
                    int currentRetryCount = (Integer) dlqData.getOrDefault("retryCount", 0);

                    // 최대 재시도 횟수 초과 시 스킵
                    if (currentRetryCount >= MAX_RETRY_COUNT) {
                        log.warn("[DLQ Scheduler] 최대 재시도 횟수 초과 - requestId: {}, retryCount: {}",
                            dlqData.get("requestId"), currentRetryCount);
                        skipCount++;
                        continue;
                    }

                    // 에러 메시지에서 재시도 가능 여부 판단
                    String errorMessage = (String) dlqData.get("errorMessage");
                    if (!isRetryableError(errorMessage)) {
                        log.info("[DLQ Scheduler] 재시도 불가능한 에러 - requestId: {}", dlqData.get("requestId"));
                        skipCount++;
                        continue;
                    }

                    // 원본 메시지 재구성 및 재전송
                    CouponIssuedEvent event = reconstructEvent(dlqData);
                    if (event != null) {
                        resendToMainTopic(event);
                        retryCount++;
                        log.info("[DLQ Scheduler] 메시지 재전송 완료 - requestId: {}, retryCount: {}",
                            event.getRequestId(), currentRetryCount + 1);
                    }

                } catch (Exception e) {
                    log.error("[DLQ Scheduler] 개별 메시지 재처리 실패 - key: {}, error: {}",
                        dlqKey, e.getMessage(), e);
                }
            }

            log.info("[DLQ Scheduler] DLQ 재처리 완료 - 재시도: {}, 스킵: {}", retryCount, skipCount);

        } catch (Exception e) {
            log.error("[DLQ Scheduler] DLQ 재처리 중 에러 발생", e);
        }
    }

    /**
     * 재시도 가능한 에러인지 판단
     */
    private boolean isRetryableError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        return errorMessage.contains("timeout")
            || errorMessage.contains("Timeout")
            || errorMessage.contains("Lock")
            || errorMessage.contains("Connection")
            || errorMessage.contains("TransientDataAccess");
    }

    /**
     * DLQ 데이터에서 CouponIssuedEvent 재구성
     */
    private CouponIssuedEvent reconstructEvent(Map<String, Object> dlqData) {
        try {
            String requestId = (String) dlqData.get("requestId");

            // 원본 이벤트 데이터 조회 (Redis에 저장되어 있다고 가정)
            String eventKey = "coupon:issue:event:" + requestId;
            String eventData = redisTemplate.opsForValue().get(eventKey);

            if (eventData != null) {
                return objectMapper.readValue(eventData, CouponIssuedEvent.class);
            }

            // 원본 데이터가 없으면 DLQ 데이터로 재구성
            return CouponIssuedEvent.builder()
                .requestId(requestId)
                .userId(getLongValue(dlqData, "userId"))
                .couponId(getLongValue(dlqData, "couponId"))
                .issuedAt(getLongValue(dlqData, "timestamp"))
                .build();

        } catch (Exception e) {
            log.error("[DLQ Scheduler] 이벤트 재구성 실패", e);
            return null;
        }
    }

    /**
     * 메인 토픽으로 재전송
     */
    private void resendToMainTopic(CouponIssuedEvent event) {
        try {
            String key = event.getCouponId().toString();
            kafkaTemplate.send(KafkaConfig.COUPON_ISSUED_TOPIC, key, event);
            log.info("[DLQ Scheduler] 메인 토픽 재전송 - requestId: {}, couponId: {}",
                event.getRequestId(), event.getCouponId());
        } catch (Exception e) {
            log.error("[DLQ Scheduler] 메인 토픽 재전송 실패 - requestId: {}", event.getRequestId(), e);
            throw e;
        }
    }

    /**
     * Map에서 Long 값 추출
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        }
        return null;
    }
}