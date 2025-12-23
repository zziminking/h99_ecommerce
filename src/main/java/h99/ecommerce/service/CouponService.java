package h99.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.annotation.CustomTransactional;
import h99.ecommerce.annotation.DistributedLock;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.coupon.CouponRepository;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import h99.ecommerce.domain.user.UserRepository;
import h99.ecommerce.dto.CouponIssueResponse;
import h99.ecommerce.dto.CouponIssueResult;
import h99.ecommerce.dto.CouponStatusResponse;
import h99.ecommerce.event.CouponIssuedEvent;
import h99.ecommerce.exception.CouponIssueFailedException;
import h99.ecommerce.repository.CouponRedisRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 쿠폰 발급 (선착순) - Redis 기반 + Kafka 이벤트 발행
     */
    public CouponIssueResponse issueCoupon(Long userId, Long couponId) {
        CouponIssueResult result = couponRedisRepository.issueCouponAtomic(couponId, userId);

        if (!result.isSuccess()) {
            log.warn("쿠폰 발급 실패 - userId: {}, couponId: {}, error: {}",
                userId, couponId, result.getErrorCode());
            throw new CouponIssueFailedException(result.getErrorMessage());
        }

        log.info("쿠폰 발급 성공 - userId: {}, couponId: {}, rank: {}, remainingStock: {}",
            userId, couponId, result.getRank(), result.getRemainingStock());

        String requestId = UUID.randomUUID().toString();

        Coupon coupon = couponRepository.findOne(couponId);
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: " + couponId);
        }

        CouponIssuedEvent event = CouponIssuedEvent.builder()
            .requestId(requestId)
            .userId(userId)
            .couponId(couponId)
            .issuedAt(result.getIssuedAt())
            .rank(result.getRank())
            .couponName(coupon.getName())
            .discountType(coupon.getDiscountType().name())
            .discountValue(coupon.getDiscountValue())
            .build();

        eventPublisher.publishEvent(event);

        try {
            String statusKey = "coupon:issue:status:" + requestId;
            Map<String, Object> status = Map.of(
                "status", "PROCESSING",
                "userId", userId,
                "couponId", couponId,
                "createdAt", System.currentTimeMillis()
            );
            redisTemplate.opsForValue().set(
                statusKey,
                objectMapper.writeValueAsString(status),
                10, TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("Redis 상태 저장 실패 - requestId: {}", requestId, e);
        }

        return CouponIssueResponse.accepted(requestId, result);
    }

    /**
     * 쿠폰 발급 상태 조회
     */
    public CouponStatusResponse getCouponIssueStatus(String requestId) {
        try {
            String statusKey = "coupon:issue:status:" + requestId;
            String statusData = redisTemplate.opsForValue().get(statusKey);

            if (statusData == null) {
                return CouponStatusResponse.notFound(requestId);
            }

            Map<String, Object> status = objectMapper.readValue(statusData, Map.class);
            String statusValue = (String) status.get("status");
            String message = (String) status.get("message");
            Long userId = getLongValue(status, "userId");
            Long couponId = getLongValue(status, "couponId");
            Long createdAt = getLongValue(status, "createdAt");
            Long updatedAt = getLongValue(status, "updatedAt");

            if ("COMPLETED".equals(statusValue)) {
                if (userId != null && couponId != null) {
                    Optional<UserCoupon> userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                    if (userCoupon.isPresent()) {
                        return CouponStatusResponse.completed(
                            requestId, userId, couponId, userCoupon.get().getUserCouponId(), message
                        );
                    }
                }
                return CouponStatusResponse.completed(requestId, userId, couponId, null, message);
            } else if ("FAILED".equals(statusValue)) {
                return CouponStatusResponse.failed(requestId, message);
            } else {
                return CouponStatusResponse.processing(requestId, message);
            }

        } catch (Exception e) {
            log.error("쿠폰 발급 상태 조회 실패 - requestId: {}", requestId, e);
            return CouponStatusResponse.failed(requestId, "상태 조회 중 오류가 발생했습니다.");
        }
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        }
        return null;
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
