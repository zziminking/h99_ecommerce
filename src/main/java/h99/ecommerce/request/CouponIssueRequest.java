package h99.ecommerce.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "쿠폰 발급 요청")
public class CouponIssueRequest {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    private Long userId;

    @Schema(description = "쿠폰 ID", example = "1", required = true)
    private Long couponId;
}
