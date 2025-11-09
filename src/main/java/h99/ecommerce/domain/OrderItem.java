package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderItem {

    private int orderItemId;
    private int orderId;
    private int productId;
    private int quantity;
    private OrderStatus status;
    private BigDecimal totalAmount;    // 상품 가격 * 수량
    private BigDecimal discountAmount; // 할인 금액
    private BigDecimal finalAmount;    // 최종 금액 (totalAmount - discountAmount)
    private BigDecimal price;          // 상품 단가
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OrderItem(int orderItemId, int orderId, int productId, int quantity, OrderStatus status,
                     BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal finalAmount,
                     BigDecimal price, LocalDateTime createdAt, LocalDateTime updatedAt) {
        validateQuantity(quantity);
        validatePrice(price);
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status == null ? OrderStatus.PENDING : status;
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        this.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        this.finalAmount = finalAmount == null ? BigDecimal.ZERO : finalAmount;
        this.price = price;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    /**
     * 총 금액 계산 (상품 가격 * 수량)
     */
    public void calculateTotalAmount() {
        this.totalAmount = this.price.multiply(new BigDecimal(this.quantity));
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 최종 금액 계산 (총 금액 - 할인 금액)
     */
    public void calculateFinalAmount() {
        this.finalAmount = this.totalAmount.subtract(this.discountAmount);
        if (this.finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.finalAmount = BigDecimal.ZERO;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 할인 적용
     */
    public void applyDiscount(BigDecimal discountAmount) {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
        }
        this.discountAmount = discountAmount;
        calculateFinalAmount();
    }

    /**
     * 상태 변경
     */
    public void changeStatus(OrderStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("상태는 null일 수 없습니다.");
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 완료 상태 확인
     */
    public boolean isCompleted() {
        return this.status == OrderStatus.COMPLETED;
    }

    /**
     * 취소 상태 확인
     */
    public boolean isCanceled() {
        return this.status == OrderStatus.CANCELED;
    }

    /**
     * 대기 상태 확인
     */
    public boolean isPending() {
        return this.status == OrderStatus.PENDING;
    }
}
