package h99.ecommerce.event;

import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponRepository;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventListener {
    
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    
    /**
     * 쿠폰 발급 이벤트 처리 (비동기)
     * Redis 발급 성공 후 RDB에 저장
     */
    @Async
    @EventListener
    @Transactional
    public void handleCouponIssued(CouponIssuedEvent event) {
        try {
            log.info("쿠폰 발급 이벤트 처리 시작 - userId: {}, couponId: {}", 
                event.getUserId(), event.getCouponId());
            
            // User & Coupon 조회
            User user = userRepository.findOne(event.getUserId());
            Coupon coupon = couponRepository.findOne(event.getCouponId());
            
            if (user == null || coupon == null) {
                log.error("User 또는 Coupon을 찾을 수 없음 - userId: {}, couponId: {}", 
                    event.getUserId(), event.getCouponId());
                return;
            }
            
            // UserCoupon 생성 및 저장
            LocalDateTime issuedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getIssuedAt()), 
                ZoneId.systemDefault()
            );
            
            UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .isUsed(false)
                .createdAt(issuedAt)
                .build();
            
            userCouponRepository.save(userCoupon);
            
            // Coupon의 issuedCount 증가 (RDB 동기화)
            coupon.issue();
            couponRepository.save(coupon);
            
            log.info("쿠폰 발급 RDB 저장 완료 - userId: {}, couponId: {}, rank: {}", 
                event.getUserId(), event.getCouponId(), event.getRank());
            
        } catch (Exception e) {
            log.error("쿠폰 발급 이벤트 처리 실패 - userId: {}, couponId: {}", 
                event.getUserId(), event.getCouponId(), e);
            // TODO: 재처리 로직 또는 Dead Letter Queue
        }
    }
}
