package h99.ecommerce.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 외부 시스템 동기화 리스너
 * 주문 완료 시 외부 시스템과 동기화를 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExternalSyncListener {

    /**
     * 주문 완료 외부 시스템 동기화
     */
    @Async("orderEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("외부 시스템 동기화 시작 - orderId: {}, totalPrice: {}",
                event.getOrderId(), event.getTotalPrice());

            syncToExternalSystem(event);

            log.info("외부 시스템 동기화 완료 - orderId: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("외부 시스템 동기화 실패 - orderId: {}, error: {}",
                event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 외부 시스템 동기화
     */
    private void syncToExternalSystem(OrderCompletedEvent event) {
        log.info("외부 시스템 동기화 - orderId: {}", event.getOrderId());
        // TODO: 실제 외부 시스템 동기화 로직 구현 (ERP, WMS 등)
    }
}