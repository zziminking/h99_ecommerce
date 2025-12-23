package h99.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 쿠폰 발급 상태 조회 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class CouponStatusResponse {
    private String requestId;
    private String status;
    private String message;
    private Long userId;
    private Long couponId;
    private Long userCouponId;
    private Long createdAt;
    private Long updatedAt;

    public static CouponStatusResponse processing(String requestId, String message) {
        return CouponStatusResponse.builder()
            .requestId(requestId)
            .status("PROCESSING")
            .message(message != null ? message : "쿠폰 발급 처리 중입니다.")
            .build();
    }

    public static CouponStatusResponse completed(String requestId, Long userId, Long couponId, Long userCouponId, String message) {
        return CouponStatusResponse.builder()
            .requestId(requestId)
            .status("COMPLETED")
            .message(message != null ? message : "쿠폰 발급이 완료되었습니다.")
            .userId(userId)
            .couponId(couponId)
            .userCouponId(userCouponId)
            .build();
    }

    public static CouponStatusResponse failed(String requestId, String message) {
        return CouponStatusResponse.builder()
            .requestId(requestId)
            .status("FAILED")
            .message(message != null ? message : "쿠폰 발급에 실패했습니다.")
            .build();
    }

    public static CouponStatusResponse notFound(String requestId) {
        return CouponStatusResponse.builder()
            .requestId(requestId)
            .status("NOT_FOUND")
            .message("발급 요청을 찾을 수 없습니다.")
            .build();
    }
}