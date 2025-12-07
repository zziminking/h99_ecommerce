package h99.ecommerce.domain;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.order.OrderItem;
import h99.ecommerce.domain.order.OrderStatus;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.cartitem.CartItem;
import h99.ecommerce.domain.point.Point;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserCouponTest {

    private UserCoupon userCoupon;
    private User user;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        userCoupon = new UserCoupon(1L, user, coupon, false, null, null);
    }

    @Test
    @DisplayName("사용자 쿠폰 생성 - 성공")
    void create_user_coupon_success() {
        // given & when
        UserCoupon newUserCoupon = new UserCoupon(2L, user, coupon, false, null, null);

        // then
        assertEquals(2, newUserCoupon.getUserCouponId());
        assertEquals(200, newUserCoupon.getUser());
        assertEquals(2, newUserCoupon.getCoupon());
        assertFalse(newUserCoupon.isUsed());
        assertNull(newUserCoupon.getUsedAt());
        assertNotNull(newUserCoupon.getCreatedAt());
    }

    @Test
    @DisplayName("쿠폰 사용 - 성공")
    void use_coupon_success() {
        // when
        userCoupon.use();

        // then
        assertTrue(userCoupon.isUsed());
        assertNotNull(userCoupon.getUsedAt());
    }

    @Test
    @DisplayName("쿠폰 사용 - 이미 사용된 쿠폰 실패")
    void use_coupon_already_used_fail() {
        // given
        userCoupon.use();

        // when & then
        assertThrows(IllegalStateException.class, () ->
                userCoupon.use()
        );
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부 - 사용 가능")
    void can_use_true() {
        // when
        boolean result = userCoupon.canUse();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부 - 사용 불가")
    void can_use_false() {
        // given
        userCoupon.use();

        // when
        boolean result = userCoupon.canUse();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("쿠폰 사용 여부 확인 - 사용됨")
    void is_used_true() {
        // given
        userCoupon.use();

        // when
        boolean result = userCoupon.isUsed();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("쿠폰 사용 여부 확인 - 미사용")
    void is_used_false() {
        // when
        boolean result = userCoupon.isUsed();

        // then
        assertFalse(result);
    }
}
