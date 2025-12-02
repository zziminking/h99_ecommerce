package h99.ecommerce.domain.order;

import h99.ecommerce.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "total_quantity")
    private int totalQuantity;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @Column(name = "order_at")
    private LocalDateTime orderAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    public Order(Long orderId, User user, int totalQuantity, BigDecimal totalPrice,
                 LocalDateTime orderAt, LocalDateTime createdAt, LocalDateTime updatedAt,
                 List<OrderItem> orderItems) {
        this.orderId = orderId;
        this.user = user;
        this.totalQuantity = totalQuantity;
        this.totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice;
        this.orderAt = orderAt == null ? LocalDateTime.now() : orderAt;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
        this.orderItems = orderItems == null ? new ArrayList<>() : orderItems;
    }

    public Order() {
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

    /**
     * Service 레이어를 위한 편의 메서드 - User ID 반환
     */
    public Long getUserId() {
        return user != null ? user.getUserId() : null;
    }

    @PrePersist  // INSERT 직전 실행
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.totalPrice == null) {
            this.totalPrice = BigDecimal.ZERO;
        }
    }

    @PreUpdate  // UPDATE 직전 실행
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
