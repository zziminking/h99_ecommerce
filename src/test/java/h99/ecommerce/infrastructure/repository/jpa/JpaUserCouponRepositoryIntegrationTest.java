package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.CouponStatus;
import h99.ecommerce.domain.DiscountType;
import h99.ecommerce.domain.User;
import h99.ecommerce.domain.UserCoupon;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaUserCouponRepository 통합 테스트")
class JpaUserCouponRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaUserCouponRepository userCouponRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaCouponRepository couponRepository;

    @Test
    @DisplayName("사용자 쿠폰 저장")
    void save_user_coupon() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(BigDecimal.valueOf(10000))
                .build();
        User savedUser = userRepository.save(user);

        Coupon coupon = Coupon.builder()
                .name("10% 할인 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .maxIssueCount(100)
                .issuedCount(0)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);
        flushAndClear();

        UserCoupon userCoupon = UserCoupon.builder()
                .user(savedUser)
                .coupon(savedCoupon)
                .isUsed(false)
                .build();

        // when
        UserCoupon saved = userCouponRepository.save(userCoupon);
        flushAndClear();

        // then
        assertThat(userCoupon.getUserCouponId()).isEqualTo(saved.getUserCouponId());
    }

    @Test
    @DisplayName("사용자 쿠폰 조회")
    void findOne_by_id() {
        // given
        User user = User.builder()
                .username("testUser2")
                .point(BigDecimal.valueOf(50000))
                .build();
        User savedUser = userRepository.save(user);

        Coupon coupon = Coupon.builder()
                .name("5000원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("5000"))
                .maxIssueCount(50)
                .issuedCount(0)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(7))
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        UserCoupon userCoupon = UserCoupon.builder()
                .user(savedUser)
                .coupon(savedCoupon)
                .isUsed(false)
                .build();
        UserCoupon saved = userCouponRepository.save(userCoupon);
        flushAndClear();

        // when
        UserCoupon found = userCouponRepository.findOne(saved.getUserCouponId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.isUsed()).isFalse();
    }

    @Test
    @DisplayName("사용자 ID로 쿠폰 목록 조회")
    void find_coupons_by_user_id() {
        // given
        User user = User.builder()
                .username("testUser3")
                .point(BigDecimal.valueOf(100000))
                .build();
        User savedUser = userRepository.save(user);

        Coupon coupon1 = Coupon.builder()
                .name("쿠폰1")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .maxIssueCount(100)
                .issuedCount(0)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon coupon2 = Coupon.builder()
                .name("쿠폰2")
                .discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("3000"))
                .maxIssueCount(50)
                .issuedCount(0)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(15))
                .status(CouponStatus.ACTIVE)
                .build();

        Coupon savedCoupon1 = couponRepository.save(coupon1);
        Coupon savedCoupon2 = couponRepository.save(coupon2);

        UserCoupon userCoupon1 = UserCoupon.builder()
                .user(savedUser)
                .coupon(savedCoupon1)
                .isUsed(false)
                .build();
        UserCoupon userCoupon2 = UserCoupon.builder()
                .user(savedUser)
                .coupon(savedCoupon2)
                .isUsed(false)
                .build();

        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);
        flushAndClear();

        // when
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(savedUser.getUserId());

        // then
        assertThat(userCoupons).hasSize(2);
    }

    @Test
    @DisplayName("사용자 ID와 쿠폰 ID로 쿠폰 조회")
    void find_coupon_by_user_id_and_coupon_id() {
        // given
        User user = User.builder()
                .username("testUser4")
                .point(BigDecimal.valueOf(20000))
                .build();
        User savedUser = userRepository.save(user);

        Coupon coupon = Coupon.builder()
                .name("특별 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20"))
                .maxIssueCount(10)
                .issuedCount(0)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(3))
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        UserCoupon userCoupon = UserCoupon.builder()
                .user(savedUser)
                .coupon(savedCoupon)
                .isUsed(false)
                .build();
        userCouponRepository.save(userCoupon);
        flushAndClear();

        // when
        Optional<UserCoupon> found = userCouponRepository.findByUserIdAndCouponId(
                savedUser.getUserId(), savedCoupon.getCouponId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isFalse();
    }
}
