package h99.ecommerce.service;

import h99.ecommerce.domain.*;
import h99.ecommerce.repository.CouponRepository;
import h99.ecommerce.repository.PointRepository;
import h99.ecommerce.repository.UserCouponRepository;
import h99.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PointRepository pointRepository;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private UserCoupon userCoupon;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        user = new User(1, "testuser", new BigDecimal("50000"), null, null);
        
        LocalDateTime now = LocalDateTime.now();
        userCoupon = new UserCoupon(1, 1, 1, false, null, null);
        
        coupon = new Coupon(
                1, "10% 할인 쿠폰",
                DiscountType.PERCENTAGE, new BigDecimal("10"),
                100, 50,
                now.minusDays(1), now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );
    }

    @Test
    @DisplayName("결제 처리 - 성공 (쿠폰 없음)")
    void process_payment_without_coupon_success() {
        // given
        int userId = 1;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        
        when(userRepository.findOne(userId)).thenReturn(user);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(pointRepository.generateId()).thenReturn(1);
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        paymentService.processPayment(userId, orderId, orderAmount, null);

        // then
        verify(userRepository).save(any(User.class));
        verify(pointRepository).save(any(Point.class));
        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("결제 처리 - 성공 (쿠폰 적용)")
    void process_payment_with_coupon_success() {
        // given
        int userId = 1;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        Integer userCouponId = 1;
        
        when(userRepository.findOne(userId)).thenReturn(user);
        when(userCouponRepository.findOne(userCouponId)).thenReturn(userCoupon);
        when(couponRepository.findOne(userCoupon.getCouponId())).thenReturn(coupon);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(pointRepository.generateId()).thenReturn(1);
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userCouponRepository.save(any(UserCoupon.class))).thenReturn(userCoupon);

        // when
        paymentService.processPayment(userId, orderId, orderAmount, userCouponId);

        // then
        verify(userRepository).save(any(User.class));
        verify(pointRepository).save(any(Point.class));
        verify(userCouponRepository).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("결제 처리 - 사용자 없음 실패")
    void process_payment_user_not_found_fail() {
        // given
        int userId = 999;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        
        when(userRepository.findOne(userId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.processPayment(userId, orderId, orderAmount, null)
        );
        verify(pointRepository, never()).save(any(Point.class));
    }

    @Test
    @DisplayName("결제 처리 - 포인트 부족 실패")
    void process_payment_insufficient_points_fail() {
        // given
        int userId = 1;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("100000"); // 보유 포인트보다 큼
        
        when(userRepository.findOne(userId)).thenReturn(user);

        // when & then
        assertThrows(IllegalStateException.class, () ->
                paymentService.processPayment(userId, orderId, orderAmount, null)
        );
        verify(userRepository, never()).save(any(User.class));
        verify(pointRepository, never()).save(any(Point.class));
    }

    @Test
    @DisplayName("결제 처리 - 쿠폰 없음 실패")
    void process_payment_coupon_not_found_fail() {
        // given
        int userId = 1;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        Integer userCouponId = 999;
        
        when(userRepository.findOne(userId)).thenReturn(user);
        when(userCouponRepository.findOne(userCouponId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.processPayment(userId, orderId, orderAmount, userCouponId)
        );
    }

    @Test
    @DisplayName("결제 처리 - 쿠폰 소유자 아님 실패")
    void process_payment_not_coupon_owner_fail() {
        // given
        int userId = 2; // 다른 사용자
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        Integer userCouponId = 1;
        
        User anotherUser = new User(2, "another", new BigDecimal("50000"), null, null);
        when(userRepository.findOne(userId)).thenReturn(anotherUser);
        when(userCouponRepository.findOne(userCouponId)).thenReturn(userCoupon);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.processPayment(userId, orderId, orderAmount, userCouponId)
        );
    }

    @Test
    @DisplayName("결제 처리 - 이미 사용된 쿠폰 실패")
    void process_payment_already_used_coupon_fail() {
        // given
        int userId = 1;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        Integer userCouponId = 1;
        
        UserCoupon usedCoupon = new UserCoupon(1, 1, 1, true, LocalDateTime.now(), null);
        
        when(userRepository.findOne(userId)).thenReturn(user);
        when(userCouponRepository.findOne(userCouponId)).thenReturn(usedCoupon);

        // when & then
        assertThrows(IllegalStateException.class, () ->
                paymentService.processPayment(userId, orderId, orderAmount, userCouponId)
        );
    }

    @Test
    @DisplayName("결제 처리 - 만료된 쿠폰 실패")
    void process_payment_expired_coupon_fail() {
        // given
        int userId = 1;
        int orderId = 100;
        BigDecimal orderAmount = new BigDecimal("30000");
        Integer userCouponId = 1;
        
        LocalDateTime now = LocalDateTime.now();
        Coupon expiredCoupon = new Coupon(
                1, "만료된 쿠폰",
                DiscountType.PERCENTAGE, new BigDecimal("10"),
                100, 50,
                now.minusDays(30), now.minusDays(1),
                CouponStatus.ACTIVE, null, null
        );
        
        when(userRepository.findOne(userId)).thenReturn(user);
        when(userCouponRepository.findOne(userCouponId)).thenReturn(userCoupon);
        when(couponRepository.findOne(userCoupon.getCouponId())).thenReturn(expiredCoupon);

        // when & then
        assertThrows(IllegalStateException.class, () ->
                paymentService.processPayment(userId, orderId, orderAmount, userCouponId)
        );
    }

    @Test
    @DisplayName("포인트 충전 - 성공")
    void charge_point_success() {
        // given
        int userId = 1;
        BigDecimal amount = new BigDecimal("10000");
        
        when(userRepository.findOne(userId)).thenReturn(user);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(pointRepository.generateId()).thenReturn(1);
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        paymentService.chargePoint(userId, amount);

        // then
        verify(userRepository).save(any(User.class));
        verify(pointRepository).save(any(Point.class));
    }

    @Test
    @DisplayName("포인트 충전 - 0원 이하 실패")
    void charge_point_zero_or_negative_fail() {
        // given
        int userId = 1;

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.chargePoint(userId, BigDecimal.ZERO)
        );
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.chargePoint(userId, new BigDecimal("-1000"))
        );
    }

    @Test
    @DisplayName("포인트 충전 - 사용자 없음 실패")
    void charge_point_user_not_found_fail() {
        // given
        int userId = 999;
        BigDecimal amount = new BigDecimal("10000");
        
        when(userRepository.findOne(userId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.chargePoint(userId, amount)
        );
    }

    @Test
    @DisplayName("포인트 잔액 조회 - 성공")
    void get_point_balance_success() {
        // given
        int userId = 1;
        when(userRepository.findOne(userId)).thenReturn(user);

        // when
        BigDecimal balance = paymentService.getPointBalance(userId);

        // then
        assertEquals(new BigDecimal("50000"), balance);
    }

    @Test
    @DisplayName("포인트 잔액 조회 - 사용자 없음 실패")
    void get_point_balance_user_not_found_fail() {
        // given
        int userId = 999;
        when(userRepository.findOne(userId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                paymentService.getPointBalance(userId)
        );
    }
}
