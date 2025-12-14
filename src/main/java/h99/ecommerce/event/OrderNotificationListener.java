package h99.ecommerce.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 알림 리스너
 * 주문 완료 시 알림 발송을 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    /**
     * 주문 완료 알림 발송
     */
    @Async("orderEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
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
