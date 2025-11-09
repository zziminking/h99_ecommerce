package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class Point {

    private int pointId;
    private int orderId;
    private int userId;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public Point(int pointId, int orderId, int userId, BigDecimal amount, LocalDateTime createdAt) {
        validateAmount(amount);
        this.pointId = pointId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
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
