package h99.ecommerce.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 이벤트
 * Redis Stream에 저장될 이벤트 객체
 */
@Getter
@ToString
@Builder
public class CouponIssueEvent {

    private final Long couponId;
    private final Long userId;
    private final LocalDateTime issuedAt;
    private final String requestId; // 멱등성 보장을 위한 요청 ID

    @JsonCreator
    public CouponIssueEvent(
            @JsonProperty("couponId") Long couponId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("issuedAt") LocalDateTime issuedAt,
            @JsonProperty("requestId") String requestId
    ) {
        this.couponId = couponId;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.requestId = requestId;
    }

    public static CouponIssueEvent of(Long couponId, Long userId) {
        return CouponIssueEvent.builder()
                .couponId(couponId)
                .userId(userId)
                .issuedAt(LocalDateTime.now())
                .requestId(generateRequestId(couponId, userId))
                .build();
    }

    private static String generateRequestId(Long couponId, Long userId) {
        return String.format("coupon:%d:user:%d:%d", couponId, userId, System.currentTimeMillis());
    }
}
