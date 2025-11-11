package h99.ecommerce.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class CouponTest {

    private Coupon coupon;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        coupon = new Coupon(
                1L, "10% 할인 쿠폰",
                DiscountType.PERCENTAGE, new BigDecimal("10"),
                100, 50,
                now.minusDays(1), now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );
    }

    @Test
    @DisplayName("쿠폰 생성 - 성공")
    void create_coupon_success() {
        // given & when
        Coupon newCoupon = new Coupon(
                2L, "5000원 할인",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now, now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );

        // then
        assertEquals(2, newCoupon.getCouponId());
        assertEquals("5000원 할인", newCoupon.getName());
        assertEquals(DiscountType.FIXED, newCoupon.getDiscountType());
        assertEquals(new BigDecimal("5000"), newCoupon.getDiscountValue());
        assertEquals(50, newCoupon.getMaxIssueCount());
        assertEquals(0, newCoupon.getIssuedCount());
        assertEquals(CouponStatus.ACTIVE, newCoupon.getStatus());
        assertNotNull(newCoupon.getCreatedAt());
        assertNotNull(newCoupon.getUpdatedAt());
    }

    @Test
    @DisplayName("쿠폰 생성 - 할인 값 0 이하 실패")
    void create_coupon_with_zero_discount_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new Coupon(2L, "잘못된 쿠폰", DiscountType.FIXED, BigDecimal.ZERO,
                        50, 0, now, now.plusDays(30), CouponStatus.ACTIVE, null, null)
        );
    }

    @Test
    @DisplayName("쿠폰 생성 - 할인율 100% 초과 실패")
    void create_coupon_with_percentage_over_100_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new Coupon(2L, "잘못된 쿠폰", DiscountType.PERCENTAGE, new BigDecimal("101"),
                        50, 0, now, now.plusDays(30), CouponStatus.ACTIVE, null, null)
        );
    }

    @Test
    @DisplayName("쿠폰 생성 - 최대 발급 수량 0 이하 실패")
    void create_coupon_with_zero_max_issue_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new Coupon(2L, "잘못된 쿠폰", DiscountType.FIXED, new BigDecimal("5000"),
                        0, 0, now, now.plusDays(30), CouponStatus.ACTIVE, null, null)
        );
    }

    @Test
    @DisplayName("쿠폰 생성 - 종료일이 시작일보다 이전 실패")
    void create_coupon_with_invalid_period_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new Coupon(2L, "잘못된 쿠폰", DiscountType.FIXED, new BigDecimal("5000"),
                        50, 0, now.plusDays(30), now, CouponStatus.ACTIVE, null, null)
        );
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 - 가능")
    void can_issue_true() {
        // when
        boolean result = coupon.canIssue();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 - 비활성 상태")
    void can_issue_inactive_status() {
        // given
        coupon.deactivate();

        // when
        boolean result = coupon.canIssue();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 - 수량 소진")
    void can_issue_max_count_reached() {
        // given
        Coupon fullCoupon = new Coupon(
                2L, "소진된 쿠폰",
                DiscountType.FIXED, new BigDecimal("5000"),
                10, 10,
                now.minusDays(1), now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );

        // when
        boolean result = fullCoupon.canIssue();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 - 시작일 이전")
    void can_issue_before_start_date() {
        // given
        Coupon futureCoupon = new Coupon(
                2L, "미래 쿠폰",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now.plusDays(10), now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );

        // when
        boolean result = futureCoupon.canIssue();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 - 종료일 이후")
    void can_issue_after_end_date() {
        // given
        Coupon expiredCoupon = new Coupon(
                2L, "만료된 쿠폰",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now.minusDays(30), now.minusDays(1),
                CouponStatus.ACTIVE, null, null
        );

        // when
        boolean result = expiredCoupon.canIssue();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공")
    void issue_coupon_success() {
        // given
        int initialCount = coupon.getIssuedCount();

        // when
        coupon.issue();

        // then
        assertEquals(initialCount + 1, coupon.getIssuedCount());
    }

    @Test
    @DisplayName("쿠폰 발급 - 수량 초과 실패")
    void issue_coupon_max_count_fail() {
        // given
        Coupon fullCoupon = new Coupon(
                2L, "소진 임박 쿠폰",
                DiscountType.FIXED, new BigDecimal("5000"),
                10, 10,
                now.minusDays(1), now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );

        // when & then
        assertThrows(IllegalStateException.class, () ->
                fullCoupon.issue()
        );
    }

    @Test
    @DisplayName("쿠폰 발급 - 발급 불가능 상태 실패")
    void issue_coupon_cannot_issue_fail() {
        // given
        coupon.deactivate();

        // when & then
        assertThrows(IllegalStateException.class, () ->
                coupon.issue()
        );
    }

    @Test
    @DisplayName("쿠폰 활성화 - 성공")
    void activate_coupon_success() {
        // given
        coupon.deactivate();

        // when
        coupon.activate();

        // then
        assertEquals(CouponStatus.ACTIVE, coupon.getStatus());
    }

    @Test
    @DisplayName("쿠폰 비활성화 - 성공")
    void deactivate_coupon_success() {
        // when
        coupon.deactivate();

        // then
        assertEquals(CouponStatus.INACTIVE, coupon.getStatus());
    }

    @Test
    @DisplayName("할인 금액 계산 - 정액 할인")
    void calculate_discount_amount_fixed() {
        // given
        Coupon fixedCoupon = new Coupon(
                2L, "5000원 할인",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now, now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );
        BigDecimal orderAmount = new BigDecimal("10000");

        // when
        BigDecimal discount = fixedCoupon.calculateDiscountAmount(orderAmount);

        // then
        assertEquals(new BigDecimal("5000"), discount);
    }

    @Test
    @DisplayName("할인 금액 계산 - 정액 할인이 주문 금액보다 큼")
    void calculate_discount_amount_fixed_greater_than_order() {
        // given
        Coupon fixedCoupon = new Coupon(
                2L, "5000원 할인",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now, now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );
        BigDecimal orderAmount = new BigDecimal("3000");

        // when
        BigDecimal discount = fixedCoupon.calculateDiscountAmount(orderAmount);

        // then
        assertEquals(new BigDecimal("3000"), discount); // 주문 금액을 초과할 수 없음
    }

    @Test
    @DisplayName("할인 금액 계산 - 정률 할인")
    void calculate_discount_amount_percentage() {
        // given
        BigDecimal orderAmount = new BigDecimal("10000");

        // when
        BigDecimal discount = coupon.calculateDiscountAmount(orderAmount);

        // then
        assertEquals(new BigDecimal("1000"), discount); // 10% of 10000
    }

    @Test
    @DisplayName("할인 금액 계산 - 음수 주문 금액 실패")
    void calculate_discount_amount_negative_order_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                coupon.calculateDiscountAmount(new BigDecimal("-1000"))
        );
    }

    @Test
    @DisplayName("쿠폰 유효 기간 확인 - 유효함")
    void is_valid_period_true() {
        // when
        boolean result = coupon.isValidPeriod();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("쿠폰 유효 기간 확인 - 만료됨")
    void is_valid_period_false() {
        // given
        Coupon expiredCoupon = new Coupon(
                2L, "만료된 쿠폰",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now.minusDays(30), now.minusDays(1),
                CouponStatus.ACTIVE, null, null
        );

        // when
        boolean result = expiredCoupon.isValidPeriod();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("쿠폰 활성 상태 확인 - true")
    void is_active_true() {
        // when
        boolean result = coupon.isActive();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("쿠폰 활성 상태 확인 - false")
    void is_active_false() {
        // given
        coupon.deactivate();

        // when
        boolean result = coupon.isActive();

        // then
        assertFalse(result);
    }
}
