package h99.ecommerce.domain.coupon;

import h99.ecommerce.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupon")
@Getter
@Builder
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCouponId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "is_used")
    private boolean isUsed;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UserCoupon(Long userCouponId, User user, Coupon coupon, boolean isUsed,
                      LocalDateTime usedAt, LocalDateTime createdAt) {
        this.userCouponId = userCouponId;
        this.user = user;
        this.coupon = coupon;
        this.isUsed = isUsed;
        this.usedAt = usedAt;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public UserCoupon() {
        
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

    public Long getUserId() {
        return user != null ? user.getUserId() : null;
    }

    public Long getCouponId() {
        return coupon != null ? coupon.getCouponId() : null;
    }
}
