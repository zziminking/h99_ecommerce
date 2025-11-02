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
@Schema(description = "사용자 쿠폰 정보")
public class UserCouponDto {
    
    private Integer userCouponId;
    private Integer userId;
    private Integer couponId;
    private String couponName;
    private String discountType;
    private BigDecimal discountValue;
    private Boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime endAt;
}
