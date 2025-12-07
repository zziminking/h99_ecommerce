package h99.ecommerce.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 쿠폰 발급 결과 DTO
 * Redis Lua Script 실행 결과를 담는 객체
 */
@Getter
@Builder
public class CouponIssueResult {
    private boolean success;
    private Long couponId;
    private Long userId;
    private Long issuedAt;
    private Integer remainingStock;
    private Integer rank;
    private String errorCode;
    private String errorMessage;
    
    public static CouponIssueResult success(Long couponId, Long userId, Long issuedAt, 
                                            Integer remainingStock, Integer rank) {
        return CouponIssueResult.builder()
            .success(true)
            .couponId(couponId)
            .userId(userId)
            .issuedAt(issuedAt)
            .remainingStock(remainingStock)
            .rank(rank)
            .build();
    }
    
    public static CouponIssueResult failure(String errorCode, String errorMessage) {
        return CouponIssueResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
