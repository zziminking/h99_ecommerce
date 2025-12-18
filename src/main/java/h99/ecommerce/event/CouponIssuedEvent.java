package h99.ecommerce.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 쿠폰 발급 완료 이벤트
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponIssuedEvent {
    private String requestId;
    private Long userId;
    private Long couponId;
    private Long issuedAt;
    private Integer rank;

    private String couponName;
    private String discountType;
    private BigDecimal discountValue;
}
