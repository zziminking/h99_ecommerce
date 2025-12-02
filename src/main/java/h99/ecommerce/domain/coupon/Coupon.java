package h99.ecommerce.domain.coupon;

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
@Table(name = "coupons")
@Getter
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "name")
    private String name;

    @Column(name = "discount_type")
    private DiscountType discountType;

    @Column(name = "discount_value")
    private BigDecimal discountValue;

    @Column(name = "max_issue_count", nullable = false)
    private int maxIssueCount;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "status")
    private CouponStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Coupon(Long couponId, String name, DiscountType discountType, BigDecimal discountValue,
                  int maxIssueCount, int issuedCount, LocalDateTime startAt, LocalDateTime endAt,
                  CouponStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        validateDiscountValue(discountValue, discountType);
        validateIssueCount(maxIssueCount);
        validatePeriod(startAt, endAt);

        this.couponId = couponId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxIssueCount = maxIssueCount;
        this.issuedCount = issuedCount;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status == null ? CouponStatus.ACTIVE : status;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    public Coupon() {

    }

    private void validateDiscountValue(BigDecimal discountValue, DiscountType discountType) {
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다.");
        }
        if (discountType == DiscountType.PERCENTAGE && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("할인율은 100%를 초과할 수 없습니다.");
        }
    }

    private void validateIssueCount(int maxIssueCount) {
        if (maxIssueCount <= 0) {
            throw new IllegalArgumentException("최대 발급 수량은 1 이상이어야 합니다.");
        }
    }

    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("시작일과 종료일은 필수입니다.");
        }
        if (endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("종료일은 시작일보다 이후여야 합니다.");
        }
    }

    /**
     * 쿠폰 발급 가능 여부
     */
    public boolean canIssue() {
        LocalDateTime now = LocalDateTime.now();
        return this.status == CouponStatus.ACTIVE
                && this.issuedCount < this.maxIssueCount
                && !now.isBefore(this.startAt)
                && !now.isAfter(this.endAt);
    }

    /**
     * 쿠폰 발급
     */
    public void issue() {
        if (!canIssue()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다.");
        }

        // 발급 가능 수량 확인
        if (this.issuedCount >= this.maxIssueCount) {
            throw new IllegalStateException("쿠폰 발급 가능 수량을 초과했습니다. 최대 발급: "
                    + this.maxIssueCount + ", 현재 발급: " + this.issuedCount);
        }

        this.issuedCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 활성화
     */
    public void activate() {
        this.status = CouponStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 비활성화
     */
    public void deactivate() {
        this.status = CouponStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 할인 금액 계산
     */
    public BigDecimal calculateDiscountAmount(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다.");
        }

        if (this.discountType == DiscountType.FIXED) {
            return this.discountValue.min(orderAmount);
        } else {
            // PERCENTAGE
            BigDecimal discountAmount = orderAmount.multiply(this.discountValue).divide(new BigDecimal("100"));
            return discountAmount.min(orderAmount);
        }
    }

    /**
     * 쿠폰 유효 기간 확인
     */
    public boolean isValidPeriod() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(this.startAt) && !now.isAfter(this.endAt);
    }

    /**
     * 쿠폰 활성 상태 확인
     */
    public boolean isActive() {
        return this.status == CouponStatus.ACTIVE;
    }
}
