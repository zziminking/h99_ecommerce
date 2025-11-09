package h99.ecommerce.service;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.Point;
import h99.ecommerce.domain.User;
import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.repository.CouponRepository;
import h99.ecommerce.repository.PointRepository;
import h99.ecommerce.repository.UserCouponRepository;
import h99.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final PointRepository pointRepository;

    /**
     * 결제 처리
     * 
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param orderAmount 주문 금액
     * @param userCouponId 사용할 쿠폰 ID (nullable)
     * @throws IllegalStateException 포인트 부족, 쿠폰 만료 등
     */
    public void processPayment(int userId, int orderId, BigDecimal orderAmount, Integer userCouponId) {
        // 1. 사용자 조회
        User user = userRepository.findOne(userId);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + userId);
        }

        BigDecimal finalAmount = orderAmount;
        UserCoupon userCoupon = null;
        Coupon coupon = null;

        // 2. 쿠폰 사용 검증 및 할인 계산 (Optional)
        if (userCouponId != null) {
            userCoupon = userCouponRepository.findOne(userCouponId);
            if (userCoupon == null) {
                throw new IllegalArgumentException("쿠폰을 찾을 수 없습니다. userCouponId: " + userCouponId);
            }

            // 쿠폰 소유자 확인
            if (userCoupon.getUserId() != userId) {
                throw new IllegalArgumentException("해당 쿠폰을 사용할 권한이 없습니다.");
            }

            // 쿠폰 사용 가능 여부 확인
            if (!userCoupon.canUse()) {
                throw new IllegalStateException("이미 사용된 쿠폰입니다.");
            }

            // 쿠폰 정보 조회
            coupon = couponRepository.findOne(userCoupon.getCouponId());
            if (coupon == null) {
                throw new IllegalArgumentException("쿠폰 정보를 찾을 수 없습니다.");
            }

            // 쿠폰 유효 기간 확인
            if (!coupon.isValidPeriod()) {
                throw new IllegalStateException("쿠폰 유효기간이 만료되었습니다.");
            }

            // 쿠폰 활성 상태 확인
            if (!coupon.isActive()) {
                throw new IllegalStateException("사용할 수 없는 쿠폰입니다.");
            }

            // 할인 금액 계산
            BigDecimal discountAmount = coupon.calculateDiscountAmount(orderAmount);
            finalAmount = orderAmount.subtract(discountAmount);
            
            // 최종 금액이 0보다 작으면 0으로 설정
            if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                finalAmount = BigDecimal.ZERO;
            }
        }

        // 3. 포인트 잔액 확인
        if (!user.hasEnoughPoint(finalAmount)) {
            throw new IllegalStateException("포인트 잔액이 부족합니다. 현재 잔액: " + user.getPoint() 
                    + ", 필요 금액: " + finalAmount);
        }

        // 4. 포인트 차감
        user.deductPoint(finalAmount);
        userRepository.save(user);

        // 5. 포인트 사용 내역 저장 (음수로 저장)
        int pointId = pointRepository.generateId();
        Point point = Point.builder()
                .pointId(pointId)
                .orderId(orderId)
                .userId(userId)
                .amount(finalAmount.negate()) // 사용은 음수로 저장
                .build();
        pointRepository.save(point);

        // 6. 쿠폰 사용 처리 (Optional)
        if (userCoupon != null) {
            userCoupon.use();
            userCouponRepository.save(userCoupon);
        }
    }

    /**
     * 포인트 충전
     */
    public void chargePoint(int userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        User user = userRepository.findOne(userId);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + userId);
        }

        // 포인트 충전
        user.chargePoint(amount);
        userRepository.save(user);

        // 포인트 충전 내역 저장 (양수로 저장)
        int pointId = pointRepository.generateId();
        Point point = Point.builder()
                .pointId(pointId)
                .orderId(0) // 충전은 주문 ID 없음
                .userId(userId)
                .amount(amount)
                .build();
        pointRepository.save(point);
    }

    /**
     * 포인트 잔액 조회
     */
    public BigDecimal getPointBalance(int userId) {
        User user = userRepository.findOne(userId);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + userId);
        }
        return user.getPoint();
    }
}
