package h99.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "쿠폰 정보")
public class CouponDto {
    
    private Integer couponId;
    private String name;
    private String discountType;
    private BigDecimal discountValue;
    private Integer maxIssueCount;
    private Integer issuedCount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
}
