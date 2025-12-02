package h99.ecommerce.service;

import h99.ecommerce.annotation.CustomTransactional;
import h99.ecommerce.annotation.DistributedLock;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.coupon.CouponRepository;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import h99.ecommerce.domain.user.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    /**
     * 쿠폰 발급 (선착순)
     * 동시성 제어를 위해 synchronized 적용
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     * @throws IllegalStateException 중복 발급, 쿠폰 소진 등
     */
    @DistributedLock(key = "coupon:issue:#{#couponId}")
    @CustomTransactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        Optional<UserCoupon> existingUserCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingUserCoupon.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        Coupon coupon = couponRepository.findOne(couponId);
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + couponId);
        }

        if (!coupon.canIssue()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다. 활성 상태, 유효 기간, 수량을 확인하세요.");
        }

        coupon.issue();
        couponRepository.save(coupon);

        User user = userRepository.findOne(userId);
        UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .isUsed(false)
                .usedAt(null)
                .build();

        userCouponRepository.save(userCoupon);

        log.info("쿠폰 발급 완료 - userId: {}, couponId: {}", userId, couponId);

        return userCoupon;
    }

    /**
     * 사용자 쿠폰 목록 조회
     */
    public List<UserCoupon> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    /**
     * 사용 가능한 쿠폰 목록 조회 (미사용 + 유효 기간 내)
     */
    public List<UserCoupon> getAvailableUserCoupons(Long userId) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);
        
        return userCoupons.stream()
                .filter(UserCoupon::canUse)
                .filter(it -> {
                    Coupon coupon = couponRepository.findOne(it.getCouponId());
                    return coupon != null && coupon.isValidPeriod() && coupon.isActive();
                })
                .toList();
    }

    /**
     * 쿠폰 상세 조회
     */
    public Coupon getCoupon(Long couponId) {
        Coupon coupon = couponRepository.findOne(couponId);
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + couponId);
        }
        return coupon;
    }

    /**
     * 쿠폰 목록 조회 (활성 쿠폰만)
     */
    public List<Coupon> getActiveCoupons() {
        return couponRepository.findAll().stream()
                .filter(Coupon::isActive)
                .toList();
    }

    /**
     * 쿠폰 활성화
     */
    @Transactional
    public void activateCoupon(Long couponId) {
        Coupon coupon = couponRepository.findOne(couponId);
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + couponId);
        }
        
        coupon.activate();
        couponRepository.save(coupon);
        
        log.info("쿠폰 활성화 완료 - couponId: {}", couponId);
    }

    /**
     * 쿠폰 비활성화
     */
    @Transactional
    public void deactivateCoupon(Long couponId) {
        Coupon coupon = couponRepository.findOne(couponId);
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + couponId);
        }
        
        coupon.deactivate();
        couponRepository.save(coupon);
        
        log.info("쿠폰 비활성화 완료 - couponId: {}", couponId);
    }

    /**
     * 사용자 쿠폰 검증 (결제 시 사용)
     */
    public void validateUserCoupon(Long userId, Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findOne(userCouponId);
        if (userCoupon == null) {
            throw new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다.");
        }

        // 소유자 확인
        if (!Objects.equals(userCoupon.getUserId(), userId)) {
            throw new IllegalArgumentException("쿠폰 소유자가 아닙니다.");
        }

        // 사용 가능 여부 확인
        if (!userCoupon.canUse()) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }

        // 쿠폰 유효성 확인
        Coupon coupon = couponRepository.findOne(userCoupon.getCouponId());
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰 정보를 찾을 수 없습니다.");
        }

        if (!coupon.isValidPeriod()) {
            throw new IllegalStateException("쿠폰 유효기간이 만료되었습니다.");
        }

        if (!coupon.isActive()) {
            throw new IllegalStateException("사용할 수 없는 쿠폰입니다.");
        }
    }
}
