package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class User {

    private int userId;
    private String username;
    private BigDecimal point;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User(int userId, String username, BigDecimal point, LocalDateTime createdAt, LocalDateTime updatedAt) {
        validateUsername(username);
        this.userId = userId;
        this.username = username;
        this.point = point == null ? BigDecimal.ZERO : point;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자명은 필수입니다.");
        }
    }

    /**
     * 포인트 충전
     */
    public void chargePoint(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
        this.point = this.point.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 포인트 차감
     */
    public void deductPoint(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }
        if (this.point.compareTo(amount) < 0) {
            throw new IllegalStateException("포인트 잔액이 부족합니다.");
        }
        this.point = this.point.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 포인트 확인
     */
    public boolean hasEnoughPoint(BigDecimal amount) {
        return this.point.compareTo(amount) >= 0;
    }
}
