package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserCoupon {

    private int userCouponId;
    private int userId;
    private int couponId;
    private boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;

    public UserCoupon(int userCouponId, int userId, int couponId, boolean isUsed, 
                      LocalDateTime usedAt, LocalDateTime createdAt) {
        this.userCouponId = userCouponId;
        this.userId = userId;
        this.couponId = couponId;
        this.isUsed = isUsed;
        this.usedAt = usedAt;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    /**
     * 쿠폰 사용
     */
    public void use() {
        if (this.isUsed) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 사용 가능 여부
     */
    public boolean canUse() {
        return !this.isUsed;
    }

    /**
     * 쿠폰 사용 여부 확인
     */
    public boolean isUsed() {
        return this.isUsed;
    }
}
