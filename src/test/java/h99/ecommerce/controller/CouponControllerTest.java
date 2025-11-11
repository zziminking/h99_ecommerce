package h99.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.CouponStatus;
import h99.ecommerce.domain.DiscountType;
import h99.ecommerce.domain.User;
import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CouponService couponService;

    @Test
    @DisplayName("활성 쿠폰 목록 조회 API 성공")
    void getActiveCoupons_Success() throws Exception {
        // given
        List<Coupon> coupons = Arrays.asList(
                Coupon.builder()
                        .couponId(1L)
                        .name("10% 할인 쿠폰")
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(new BigDecimal("10"))
                        .maxIssueCount(100)
                        .issuedCount(50)
                        .startAt(LocalDateTime.now())
                        .endAt(LocalDateTime.now().plusDays(30))
                        .status(CouponStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                Coupon.builder()
                        .couponId(2L)
                        .name("5000원 할인 쿠폰")
                        .discountType(DiscountType.FIXED)
                        .discountValue(new BigDecimal("5000"))
                        .maxIssueCount(50)
                        .issuedCount(30)
                        .startAt(LocalDateTime.now())
                        .endAt(LocalDateTime.now().plusDays(30))
                        .status(CouponStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        given(couponService.getActiveCoupons()).willReturn(coupons);

        // when & then
        mockMvc.perform(get("/api/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].couponId").value(1))
                .andExpect(jsonPath("$[0].name").value("10% 할인 쿠폰"))
                .andExpect(jsonPath("$[0].discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$[0].discountValue").value(10))
                .andExpect(jsonPath("$[1].couponId").value(2))
                .andExpect(jsonPath("$[1].name").value("5000원 할인 쿠폰"))
                .andExpect(jsonPath("$[1].discountType").value("FIXED"));
    }

    @Test
    @DisplayName("쿠폰 상세 조회 API 성공")
    void getCoupon_Success() throws Exception {
        // given
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .name("10% 할인 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .maxIssueCount(100)
                .issuedCount(50)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(couponService.getCoupon(1L)).willReturn(coupon);

        // when & then
        mockMvc.perform(get("/api/coupons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId").value(1))
                .andExpect(jsonPath("$.name").value("10% 할인 쿠폰"))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountValue").value(10))
                .andExpect(jsonPath("$.issuedCount").value(50));
    }

    @Test
    @DisplayName("쿠폰 상세 조회 API - 잘못된 couponId")
    void getCoupon_InvalidCouponId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupons/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/coupons/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("쿠폰 발급 API 성공")
    void issueCoupon_Success() throws Exception {
        // given
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .user(User.builder().build())
                .coupon(Coupon.builder().build())
                .isUsed(false)
                .createdAt(LocalDateTime.now())
                .build();
        given(couponService.issueCoupon(1L, 1L)).willReturn(userCoupon);

        // when & then
        mockMvc.perform(post("/api/coupons/1/issue")
                        .param("userId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userCouponId").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.couponId").value(1))
                .andExpect(jsonPath("$.used").value(false));

        verify(couponService).issueCoupon(1L, 1L);
    }

    @Test
    @DisplayName("쿠폰 발급 API - 잘못된 couponId")
    void issueCoupon_InvalidCouponId() throws Exception {
        // when & then
        mockMvc.perform(post("/api/coupons/0/issue")
                        .param("userId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("쿠폰 발급 API - 잘못된 userId")
    void issueCoupon_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(post("/api/coupons/1/issue")
                        .param("userId", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/coupons/1/issue")
                        .param("userId", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사용자 쿠폰 목록 조회 API 성공")
    void getUserCoupons_Success() throws Exception {
        // given
        List<UserCoupon> userCoupons = Arrays.asList(
                UserCoupon.builder()
                        .userCouponId(1L)
                        .user(User.builder().userId(1L).build())
                        .coupon(Coupon.builder().couponId(1L).build())
                        .isUsed(false)
                        .createdAt(LocalDateTime.now())
                        .build(),
                UserCoupon.builder()
                        .userCouponId(2L)
                        .user(User.builder().userId(1L).build())
                        .coupon(Coupon.builder().couponId(2L).build())
                        .isUsed(true)
                        .usedAt(LocalDateTime.now().minusDays(3))
                        .createdAt(LocalDateTime.now().minusDays(5))
                        .build()
        );
        given(couponService.getUserCoupons(1L)).willReturn(userCoupons);

        // when & then
        mockMvc.perform(get("/api/coupons/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userCouponId").value(1))
                .andExpect(jsonPath("$[0].used").value(false))
                .andExpect(jsonPath("$[1].userCouponId").value(2))
                .andExpect(jsonPath("$[1].used").value(true));
    }

    @Test
    @DisplayName("사용자 쿠폰 목록 조회 API - 잘못된 userId")
    void getUserCoupons_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupons/users/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/coupons/users/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 목록 조회 API 성공")
    void getAvailableUserCoupons_Success() throws Exception {
        // given
        List<UserCoupon> availableCoupons = Arrays.asList(
                UserCoupon.builder()
                        .userCouponId(1L)
                        .user(User.builder().userId(1L).build())
                        .coupon(Coupon.builder().couponId(1L).build())
                        .isUsed(false)
                        .createdAt(LocalDateTime.now())
                        .build(),
                UserCoupon.builder()
                        .userCouponId(3L)
                        .user(User.builder().userId(1L).build())
                        .coupon(Coupon.builder().couponId(3L).build())
                        .isUsed(false)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
        given(couponService.getAvailableUserCoupons(1L)).willReturn(availableCoupons);

        // when & then
        mockMvc.perform(get("/api/coupons/users/1/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userCouponId").value(1))
                .andExpect(jsonPath("$[0].used").value(false))
                .andExpect(jsonPath("$[1].userCouponId").value(3))
                .andExpect(jsonPath("$[1].used").value(false));
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 목록 조회 API - 잘못된 userId")
    void getAvailableUserCoupons_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupons/users/0/available"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/coupons/users/-1/available"))
                .andExpect(status().isBadRequest());
    }
}
