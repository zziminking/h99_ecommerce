package h99.ecommerce.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 쿠폰 발급 완료 이벤트
 * Redis 발급 성공 후 RDB 저장을 위해 발행
 */
@Getter
@AllArgsConstructor
public class CouponIssuedEvent {
    private Long userId;
    private Long couponId;
    private Long issuedAt;  // timestamp (ms)
    private Integer rank;    // 발급 순위
}
