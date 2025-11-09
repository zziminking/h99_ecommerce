package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Order {

    private int orderId;
    private int userId;
    private int totalQuantity;
    private BigDecimal totalPrice;
    private LocalDateTime orderAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    public Order(int orderId, int userId, int totalQuantity, BigDecimal totalPrice, 
                 LocalDateTime orderAt, LocalDateTime createdAt, LocalDateTime updatedAt, 
                 List<OrderItem> orderItems) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalQuantity = totalQuantity;
        this.totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice;
        this.orderAt = orderAt == null ? LocalDateTime.now() : orderAt;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
        this.orderItems = orderItems == null ? new ArrayList<>() : orderItems;
    }

    /**
     * 주문 아이템 추가
     */
    public void addOrderItem(OrderItem orderItem) {
        if (orderItem == null) {
            throw new IllegalArgumentException("주문 아이템은 null일 수 없습니다.");
        }
        this.orderItems.add(orderItem);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 총 금액 계산
     */
    public void calculateTotalPrice() {
        this.totalPrice = orderItems.stream()
                .map(OrderItem::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 총 수량 계산
     */
    public void calculateTotalQuantity() {
        this.totalQuantity = orderItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 완료 여부
     */
    public boolean isCompleted() {
        return orderItems.stream()
                .allMatch(OrderItem::isCompleted);
    }
}
