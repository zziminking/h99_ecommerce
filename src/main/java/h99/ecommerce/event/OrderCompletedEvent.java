package h99.ecommerce.event;

import h99.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 주문 완료 이벤트
 */
@Getter
@AllArgsConstructor
public class OrderCompletedEvent {
    private Long orderId;
    private Long userId;
    private BigDecimal totalPrice;
    private Integer totalQuantity;

    public static OrderCompletedEvent from(Order order, Long userId) {
        return new OrderCompletedEvent(
            order.getOrderId(),
            userId,
            order.getTotalPrice(),
            order.getTotalQuantity()
        );
    }
}
