package h99.ecommerce.service;

import h99.ecommerce.annotation.CustomTransactional;
import h99.ecommerce.annotation.DistributedLock;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.coupon.CouponRepository;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import h99.ecommerce.domain.user.UserRepository;
import h99.ecommerce.dto.CouponIssueResult;
import h99.ecommerce.event.CouponIssuedEvent;
import h99.ecommerce.exception.CouponIssueFailedException;
import h99.ecommerce.repository.CouponRedisRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CouponRedisRepository couponRedisRepository;  // ← 추가
    private final ApplicationEventPublisher eventPublisher;     // ← 추가

    /**
     * 쿠폰 발급 (선착순) - Redis 기반
     * Lua Script를 이용한 원자적 처리
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     * @throws CouponIssueFailedException 중복 발급, 쿠폰 소진 등
     */
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        // 1. Redis Lua Script로 원자적 발급 처리
        CouponIssueResult result = couponRedisRepository.issueCouponAtomic(couponId, userId);
        
        if (!result.isSuccess()) {
            log.warn("쿠폰 발급 실패 - userId: {}, couponId: {}, error: {}", 
                userId, couponId, result.getErrorCode());
            throw new CouponIssueFailedException(result.getErrorMessage());
        }
        
        log.info("쿠폰 발급 성공 - userId: {}, couponId: {}, rank: {}, remainingStock: {}", 
            userId, couponId, result.getRank(), result.getRemainingStock());
        
        // 2. 이벤트 발행 (비동기 RDB 저장)
        eventPublisher.publishEvent(new CouponIssuedEvent(
            userId,
            couponId,
            result.getIssuedAt(),
            result.getRank()
        ));
        
        // 3. UserCoupon 객체 반환 (RDB 저장 전이지만 응답용)
        Coupon coupon = couponRepository.findOne(couponId);
        User user = userRepository.findOne(userId);
        return UserCoupon.builder()
            .user(user)
            .coupon(coupon)
            .isUsed(false)
            .build();
    }
    
    /**
     * 쿠폰 생성 (RDB + Redis 초기화)
     */
    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        // 1. RDB 저장
        Coupon savedCoupon = couponRepository.save(coupon);
        
        // 2. Redis 초기화
        couponRedisRepository.initializeCoupon(savedCoupon);
        
        log.info("쿠폰 생성 완료 - couponId: {}, name: {}", 
            savedCoupon.getCouponId(), savedCoupon.getName());
        
        return savedCoupon;
    }
    
    /**
     * 발급 여부 확인 (Redis 조회)
     */
    public boolean isAlreadyIssued(Long userId, Long couponId) {
        return couponRedisRepository.isAlreadyIssued(couponId, userId);
    }
    
    /**
     * 발급 순위 조회
     */
    public Integer getCouponIssueRank(Long userId, Long couponId) {
        return couponRedisRepository.getIssueRank(couponId, userId);
    }
    
    /**
     * 선착순 Top N 조회
     */
    public List<Long> getTopIssuers(Long couponId, int limit) {
        return couponRedisRepository.getTopIssuers(couponId, limit);
    }
    
    /**
     * 재고 조회 (Redis)
     */
    public Integer getCouponStock(Long couponId) {
        return couponRedisRepository.getCouponStock(couponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰 재고 정보를 찾을 수 없습니다"));
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
