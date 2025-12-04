package h99.ecommerce.repository;

import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.dto.CouponIssueResult;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 Redis 저장소 인터페이스
 * DIP 적용: 인프라에 의존하지 않음
 */
public interface CouponRedisRepository {
    
    /**
     * 쿠폰 메타 정보 Redis 초기화
     */
    void initializeCoupon(Coupon coupon);
    
    /**
     * Lua Script를 이용한 쿠폰 발급 (원자적)
     * @return 발급 결과 (성공/실패 정보)
     */
    CouponIssueResult issueCouponAtomic(Long couponId, Long userId);
    
    /**
     * 쿠폰 재고 조회
     */
    Optional<Integer> getCouponStock(Long couponId);
    
    /**
     * 발급 여부 확인
     */
    boolean isAlreadyIssued(Long couponId, Long userId);
    
    /**
     * 발급 순위 조회
     * @return 순위 (1-based), 발급 받지 않았으면 null
     */
    Integer getIssueRank(Long couponId, Long userId);
    
    /**
     * 선착순 Top N 조회
     */
    List<Long> getTopIssuers(Long couponId, int limit);
    
    /**
     * 유저가 보유한 쿠폰 ID 목록
     */
    List<Long> getUserCouponIds(Long userId);
    
    /**
     * TTL 설정
     */
    void setExpiration(String key, long seconds);
    
    /**
     * 쿠폰 상태 업데이트
     */
    void updateCouponStatus(Long couponId, String status);
}
