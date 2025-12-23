package h99.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 쿠폰 발급 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class CouponIssueResponse {
    private String requestId;
    private String status;
    private String message;
    private Integer rank;
    private Integer remainingStock;

    public static CouponIssueResponse accepted(String requestId, CouponIssueResult result) {
        return CouponIssueResponse.builder()
            .requestId(requestId)
            .status("PROCESSING")
            .message("쿠폰 발급이 처리 중입니다. 잠시 후 상태를 확인해주세요.")
            .rank(result.getRank())
            .remainingStock(result.getRemainingStock())
            .build();
    }
}