package h99.ecommerce.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 리스너
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    /**
     * 주문 완료 이벤트 처리
     */
    @Async("orderEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("주문 완료 이벤트 처리 시작 - orderId: {}, userId: {}, totalPrice: {}", 
                event.getOrderId(), event.getUserId(), event.getTotalPrice());
            
            sendOrderNotification(event);
            syncToExternalSystem(event);
            
            log.info("주문 완료 이벤트 처리 완료 - orderId: {}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("주문 완료 이벤트 처리 실패 - orderId: {}, error: {}", 
                event.getOrderId(), e.getMessage(), e);
        }
    }
    
    /**
     * 주문 알림 발송
     */
    private void sendOrderNotification(OrderCompletedEvent event) {
        log.info("주문 알림 발송 - orderId: {}, userId: {}", event.getOrderId(), event.getUserId());
    }
    
    /**
     * 외부 시스템 동기화
     */
    private void syncToExternalSystem(OrderCompletedEvent event) {
        log.info("외부 시스템 동기화 - orderId: {}", event.getOrderId());
    }
}
