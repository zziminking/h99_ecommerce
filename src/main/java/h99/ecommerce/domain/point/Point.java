package h99.ecommerce.domain.point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "point")
@Getter
@Builder
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    private Long pointId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Point(Long pointId, Long orderId, Long userId, BigDecimal amount, LocalDateTime createdAt) {
        validateAmount(amount);
        this.pointId = pointId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public Point() {

    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("금액은 필수입니다.");
        }
    }

    /**
     * 충전 내역인지 확인 (양수)
     */
    public boolean isCharge() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 사용 내역인지 확인 (음수)
     */
    public boolean isUsage() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
