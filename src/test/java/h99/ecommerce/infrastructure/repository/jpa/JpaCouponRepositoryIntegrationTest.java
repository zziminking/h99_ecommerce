package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;

import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaCouponRepository 통합 테스트")
class JpaCouponRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaCouponRepository couponRepository;

    @Test
    @DisplayName("쿠폰 저장")
    void save_coupon() {
        // given
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

        // when
        Coupon saved = couponRepository.save(coupon);
        flushAndClear();

        // then
        assertThat(coupon.getCouponId()).isEqualTo(saved.getCouponId());
    }

    @Test
    @DisplayName("쿠폰 조회")
    void findOne_by_id() {
        // given
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
        Coupon saved = couponRepository.save(coupon);
        flushAndClear();

        // when
        Coupon found = couponRepository.findOne(saved.getCouponId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("5000원 할인 쿠폰");
        assertThat(found.getDiscountType()).isEqualTo(DiscountType.FIXED);
        assertThat(found.getDiscountValue()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("모든 쿠폰 조회")
    void find_all_coupons() {
        // given
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
        Coupon coupon3 = Coupon.builder()
                .name("쿠폰3")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20"))
                .maxIssueCount(200)
                .issuedCount(0)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(60))
                .status(CouponStatus.INACTIVE)
                .build();

        couponRepository.save(coupon1);
        couponRepository.save(coupon2);
        couponRepository.save(coupon3);
        flushAndClear();

        // when
        List<Coupon> coupons = couponRepository.findAll();

        // then
        assertThat(coupons).hasSize(3);
        assertThat(coupons).extracting("name")
                .containsExactlyInAnyOrder("쿠폰1", "쿠폰2", "쿠폰3");
    }
}
