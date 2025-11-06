package h99.ecommerce.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserCouponTest {

    private UserCoupon userCoupon;

    @BeforeEach
    void setUp() {
        userCoupon = new UserCoupon(1, 100, 1, false, null, null);
    }

    @Test
    @DisplayName("사용자 쿠폰 생성 - 성공")
    void create_user_coupon_success() {
        // given & when
        UserCoupon newUserCoupon = new UserCoupon(2, 200, 2, false, null, null);

        // then
        assertEquals(2, newUserCoupon.getUserCouponId());
        assertEquals(200, newUserCoupon.getUserId());
        assertEquals(2, newUserCoupon.getCouponId());
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
