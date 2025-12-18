package h99.ecommerce.kafka;

import h99.ecommerce.config.KafkaConfig;
import h99.ecommerce.event.CouponIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 알림을 전송하는 Consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponNotificationConsumer {

    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUED_TOPIC,
        groupId = "coupon-notification-group",
        containerFactory = "couponEventListenerContainerFactory"
    )
    public void sendNotification(
        @Payload CouponIssuedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[Notification] 쿠폰 발급 이벤트 수신 - requestId: {}, userId: {}, couponId: {}, topic: {}, partition: {}, offset: {}",
            event.getRequestId(), event.getUserId(), event.getCouponId(), topic, partition, offset);

        try {
            sendPushNotification(event);
            sendEmailNotification(event);

            log.info("[Notification] 알림 전송 완료 - requestId: {}, userId: {}, couponName: {}",
                event.getRequestId(), event.getUserId(), event.getCouponName());

        } catch (Exception e) {
            log.error("[Notification] 알림 전송 실패 - requestId: {}, error: {}",
                event.getRequestId(), e.getMessage(), e);
            throw e;
        }
    }

    private void sendPushNotification(CouponIssuedEvent event) {
        log.info("[Push] 쿠폰 발급 알림 - userId: {}, couponName: {}, rank: {}",
            event.getUserId(), event.getCouponName(), event.getRank());
        // TODO: 실제 푸시 알림 발송 로직 구현
    }

    private void sendEmailNotification(CouponIssuedEvent event) {
        log.info("[Email] 쿠폰 발급 알림 - userId: {}, couponName: {}, discountType: {}, discountValue: {}",
            event.getUserId(), event.getCouponName(), event.getDiscountType(), event.getDiscountValue());
        // TODO: 실제 이메일 발송 로직 구현
    }
}