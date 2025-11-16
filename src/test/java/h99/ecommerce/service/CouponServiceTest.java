package h99.ecommerce.service;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.CouponStatus;
import h99.ecommerce.domain.DiscountType;
import h99.ecommerce.domain.User;
import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.repository.CouponRepository;
import h99.ecommerce.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private h99.ecommerce.repository.UserRepository userRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon coupon;
    private UserCoupon userCoupon;
    private User user;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        user = User.builder()
                .userId(100L)
                .username("testUser")
                .point(new BigDecimal("100000"))
                .build();

        coupon = new Coupon(
                1L, "10% 할인 쿠폰",
                DiscountType.PERCENTAGE, new BigDecimal("10"),
                100, 50,
                now.minusDays(1), now.plusDays(30),
                CouponStatus.ACTIVE, null, null
        );

        userCoupon = new UserCoupon(1L, user, coupon, false, null, null);
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공")
    void issue_coupon_success() {
        // given
        Long userId = 100L;
        Long couponId = 1L;

        when(userRepository.findOne(userId)).thenReturn(user);
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId)).thenReturn(Optional.empty());
        when(couponRepository.findOne(couponId)).thenReturn(coupon);
        when(couponRepository.save(any(Coupon.class))).thenReturn(coupon);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserCoupon result = couponService.issueCoupon(userId, couponId);

        // then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(couponId, result.getCouponId());
        assertFalse(result.isUsed());
        verify(couponRepository).save(any(Coupon.class));
        verify(userCouponRepository).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("쿠폰 발급 - 중복 발급 실패")
    void issue_coupon_duplicate_fail() {
        // given
        Long userId = 100L;
        Long couponId = 1L;
        
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId))
                .thenReturn(Optional.of(userCoupon));

        // when & then
        assertThrows(IllegalStateException.class, () ->
                couponService.issueCoupon(userId, couponId)
        );
        verify(couponRepository, never()).save(any(Coupon.class));
        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("쿠폰 발급 - 쿠폰 없음 실패")
    void issue_coupon_not_found_fail() {
        // given
        Long userId = 100L;
        Long couponId = 1L;
        
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId)).thenReturn(Optional.empty());
        when(couponRepository.findOne(couponId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                couponService.issueCoupon(userId, couponId)
        );
    }

    @Test
    @DisplayName("쿠폰 발급 - 발급 불가능 상태 실패")
    void issue_coupon_cannot_issue_fail() {
        // given
        Long userId = 100L;
        Long couponId = 1L;
        
        coupon.deactivate(); // 비활성화
        
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId)).thenReturn(Optional.empty());
        when(couponRepository.findOne(couponId)).thenReturn(coupon);

        // when & then
        assertThrows(IllegalStateException.class, () ->
                couponService.issueCoupon(userId, couponId)
        );
        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("사용자 쿠폰 목록 조회 - 성공")
    void get_user_coupons_success() {
        // given
        Long userId = 100L;
        UserCoupon userCoupon2 = new UserCoupon(2L, user, coupon, false, null, null);
        
        when(userCouponRepository.findByUserId(userId))
                .thenReturn(Arrays.asList(userCoupon, userCoupon2));

        // when
        List<UserCoupon> result = couponService.getUserCoupons(userId);

        // then
        assertEquals(2, result.size());
        verify(userCouponRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("사용자 쿠폰 목록 조회 - 빈 목록")
    void get_user_coupons_empty() {
        // given
        Long userId = 100L;
        when(userCouponRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // when
        List<UserCoupon> result = couponService.getUserCoupons(userId);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 목록 조회 - 성공")
    void get_available_user_coupons_success() {
        // given
        Long userId = 100L;
        
        when(userCouponRepository.findByUserId(userId)).thenReturn(Arrays.asList(userCoupon));
        when(couponRepository.findOne(1L)).thenReturn(coupon);

        // when
        List<UserCoupon> result = couponService.getAvailableUserCoupons(userId);

        // then
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 목록 조회 - 사용된 쿠폰 제외")
    void get_available_user_coupons_exclude_used() {
        // given
        Long userId = 100L;
        UserCoupon usedCoupon = new UserCoupon(2L, user, coupon, true, LocalDateTime.now(), null);
        
        when(userCouponRepository.findByUserId(userId))
                .thenReturn(Arrays.asList(userCoupon, usedCoupon));
        when(couponRepository.findOne(1L)).thenReturn(coupon);

        // when
        List<UserCoupon> result = couponService.getAvailableUserCoupons(userId);

        // then
        assertEquals(1, result.size());
        assertFalse(result.get(0).isUsed());
    }

    @Test
    @DisplayName("쿠폰 상세 조회 - 성공")
    void get_coupon_success() {
        // given
        Long couponId = 1L;
        when(couponRepository.findOne(couponId)).thenReturn(coupon);

        // when
        Coupon result = couponService.getCoupon(couponId);

        // then
        assertNotNull(result);
        assertEquals(couponId, result.getCouponId());
    }

    @Test
    @DisplayName("쿠폰 상세 조회 - 쿠폰 없음 실패")
    void get_coupon_not_found_fail() {
        // given
        Long couponId = 999L;
        when(couponRepository.findOne(couponId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                couponService.getCoupon(couponId)
        );
    }

    @Test
    @DisplayName("활성 쿠폰 목록 조회 - 성공")
    void get_active_coupons_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon inactiveCoupon = new Coupon(
                2L, "비활성 쿠폰",
                DiscountType.FIXED, new BigDecimal("5000"),
                50, 0,
                now, now.plusDays(30),
                CouponStatus.INACTIVE, null, null
        );
        
        when(couponRepository.findAll()).thenReturn(Arrays.asList(coupon, inactiveCoupon));

        // when
        List<Coupon> result = couponService.getActiveCoupons();

        // then
        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
    }

    @Test
    @DisplayName("쿠폰 활성화 - 성공")
    void activate_coupon_success() {
        // given
        Long couponId = 1L;
        coupon.deactivate();
        
        when(couponRepository.findOne(couponId)).thenReturn(coupon);
        when(couponRepository.save(any(Coupon.class))).thenReturn(coupon);

        // when
        couponService.activateCoupon(couponId);

        // then
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("쿠폰 활성화 - 쿠폰 없음 실패")
    void activate_coupon_not_found_fail() {
        // given
        Long couponId = 999L;
        when(couponRepository.findOne(couponId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                couponService.activateCoupon(couponId)
        );
    }

    @Test
    @DisplayName("쿠폰 비활성화 - 성공")
    void deactivate_coupon_success() {
        // given
        Long couponId = 1L;
        when(couponRepository.findOne(couponId)).thenReturn(coupon);
        when(couponRepository.save(any(Coupon.class))).thenReturn(coupon);

        // when
        couponService.deactivateCoupon(couponId);

        // then
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("쿠폰 비활성화 - 쿠폰 없음 실패")
    void deactivate_coupon_not_found_fail() {
        // given
        Long couponId = 999L;
        when(couponRepository.findOne(couponId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                couponService.deactivateCoupon(couponId)
        );
    }

    @Test
    @DisplayName("사용자 쿠폰 검증 - 성공")
    void validate_user_coupon_success() {
        // given
        Long userId = 100L;
        Long userCouponId = 1L;
        
        when(userCouponRepository.findOne(userCouponId)).thenReturn(userCoupon);
        when(couponRepository.findOne(userCoupon.getCouponId())).thenReturn(coupon);

        // when & then
        assertDoesNotThrow(() ->
                couponService.validateUserCoupon(userId, userCouponId)
        );
    }

    @Test
    @DisplayName("사용자 쿠폰 검증 - 쿠폰 없음 실패")
    void validate_user_coupon_not_found_fail() {
        // given
        Long userId = 100L;
        Long userCouponId = 999L;
        
        when(userCouponRepository.findOne(userCouponId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                couponService.validateUserCoupon(userId, userCouponId)
        );
    }

    @Test
    @DisplayName("사용자 쿠폰 검증 - 소유자 아님 실패")
    void validate_user_coupon_not_owner_fail() {
        // given
        Long userId = 200L; // 다른 사용자
        Long userCouponId = 1L;
        
        when(userCouponRepository.findOne(userCouponId)).thenReturn(userCoupon);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                couponService.validateUserCoupon(userId, userCouponId)
        );
    }

    @Test
    @DisplayName("사용자 쿠폰 검증 - 이미 사용된 쿠폰 실패")
    void validate_user_coupon_already_used_fail() {
        // given
        Long userId = 100L;
        Long userCouponId = 1L;
        UserCoupon usedCoupon = new UserCoupon(1L, user, coupon, true, LocalDateTime.now(), null);
        
        when(userCouponRepository.findOne(userCouponId)).thenReturn(usedCoupon);

        // when & then
        assertThrows(IllegalStateException.class, () ->
                couponService.validateUserCoupon(userId, userCouponId)
        );
    }

    @Test
    @DisplayName("사용자 쿠폰 검증 - 만료된 쿠폰 실패")
    void validate_user_coupon_expired_fail() {
        // given
        Long userId = 100L;
        Long userCouponId = 1L;
        
        LocalDateTime now = LocalDateTime.now();
        Coupon expiredCoupon = new Coupon(
                1L, "만료된 쿠폰",
                DiscountType.PERCENTAGE, new BigDecimal("10"),
                100, 50,
                now.minusDays(30), now.minusDays(1),
                CouponStatus.ACTIVE, null, null
        );
        
        when(userCouponRepository.findOne(userCouponId)).thenReturn(userCoupon);
        when(couponRepository.findOne(userCoupon.getCouponId())).thenReturn(expiredCoupon);

        // when & then
        assertThrows(IllegalStateException.class, () ->
                couponService.validateUserCoupon(userId, userCouponId)
        );
    }
}
