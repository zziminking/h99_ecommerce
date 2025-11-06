package h99.ecommerce.service;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.repository.CouponRepository;
import h99.ecommerce.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급 (선착순)
     * 동시성 제어를 위해 synchronized 적용
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     * @throws IllegalStateException 중복 발급, 쿠폰 소진 등
     */
    public synchronized UserCoupon issueCoupon(int userId, int couponId) {
        // 1. 사용자 발급 내역 조회 (중복 발급 체크)
        Optional<UserCoupon> existingUserCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingUserCoupon.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        // 2. 쿠폰 정보 조회
        Coupon coupon = couponRepository.findOne(couponId);
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + couponId);
        }

        // 3. 쿠폰 발급 가능 여부 확인 (선착순 제어)
        if (!coupon.canIssue()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다. 활성 상태, 유효 기간, 수량을 확인하세요.");
        }

        // 4. 쿠폰 발급 수량 증가 (도메인 로직에서 수량 체크)
        coupon.issue();
        couponRepository.save(coupon);

        // 5. 사용자 쿠폰 발급
        int userCouponId = userCouponRepository.generateId();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(userCouponId)
                .userId(userId)
                .couponId(couponId)
                .isUsed(false)
                .usedAt(null)
                .build();

        userCouponRepository.save(userCoupon);

        log.info("쿠폰 발급 완료 - userId: {}, couponId: {}, userCouponId: {}", 
                userId, couponId, userCouponId);

        return userCoupon;
    }

    /**
     * 사용자 쿠폰 목록 조회
     */
    public List<UserCoupon> getUserCoupons(int userId) {
        return userCouponRepository.findByUserId(userId);
    }

    /**
     * 사용 가능한 쿠폰 목록 조회 (미사용 + 유효 기간 내)
     */
    public List<UserCoupon> getAvailableUserCoupons(int userId) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);
        
        return userCoupons.stream()
                .filter(UserCoupon::canUse)
                .filter(uc -> {
                    Coupon coupon = couponRepository.findOne(uc.getCouponId());
                    return coupon != null && coupon.isValidPeriod() && coupon.isActive();
                })
                .toList();
    }

    /**
     * 쿠폰 상세 조회
     */
    public Coupon getCoupon(int couponId) {
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
    public void activateCoupon(int couponId) {
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
    public void deactivateCoupon(int couponId) {
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
    public void validateUserCoupon(int userId, int userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findOne(userCouponId);
        if (userCoupon == null) {
            throw new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다.");
        }

        // 소유자 확인
        if (userCoupon.getUserId() != userId) {
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
