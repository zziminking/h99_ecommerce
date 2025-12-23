package h99.ecommerce.event;

import h99.ecommerce.kafka.CouponEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쿠폰 발급 이벤트를 Kafka로 발행하는 중간 리스너
 * 트랜잭션 커밋 후 Kafka로 이벤트를 전달하여 데이터 일관성 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponKafkaPublisher {

    private final CouponEventProducer couponEventProducer;

    /**
     * 트랜잭션 커밋 후 Kafka로 쿠폰 발급 이벤트 발행
     */
    @Async("couponEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishToKafka(CouponIssuedEvent event) {
        try {
            couponEventProducer.publishCouponIssued(event);

            log.info("[Kafka Publisher] 쿠폰 발급 이벤트 발행 성공 - requestId: {}, couponId: {}",
                event.getRequestId(), event.getCouponId());

        } catch (Exception e) {
            log.error("[Kafka Publisher] 이벤트 발행 실패 - requestId: {}, error: {}",
                event.getRequestId(), e.getMessage(), e);

            // TODO: 실패 시 보상 트랜잭션 고려
            // - Redis 상태를 FAILED로 업데이트
            // - 관리자 알림 발송
        }
    }
}