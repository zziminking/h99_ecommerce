package h99.ecommerce.controller;

import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    /**
     * 활성 쿠폰 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Coupon>> getActiveCoupons() {
        List<Coupon> coupons = couponService.getActiveCoupons();
        return ResponseEntity.ok(coupons);
    }

    /**
     * 쿠폰 상세 조회
     */
    @GetMapping("/{couponId}")
    public ResponseEntity<Coupon> getCoupon(@PathVariable Long couponId) {
        if (couponId == null || couponId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Coupon coupon = couponService.getCoupon(couponId);
        return ResponseEntity.ok(coupon);
    }

    /**
     * 쿠폰 발급
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<UserCoupon> issueCoupon(
            @PathVariable Long couponId,
            @RequestParam Long userId
    ) {
        if (couponId == null || couponId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        UserCoupon userCoupon = couponService.issueCoupon(userId, couponId);
        return ResponseEntity.status(HttpStatus.CREATED).body(userCoupon);
    }

    /**
     * 사용자 쿠폰 목록 조회
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<UserCoupon>> getUserCoupons(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        List<UserCoupon> userCoupons = couponService.getUserCoupons(userId);
        return ResponseEntity.ok(userCoupons);
    }

    /**
     * 사용 가능한 쿠폰 목록 조회
     */
    @GetMapping("/users/{userId}/available")
    public ResponseEntity<List<UserCoupon>> getAvailableUserCoupons(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        List<UserCoupon> availableCoupons = couponService.getAvailableUserCoupons(userId);
        return ResponseEntity.ok(availableCoupons);
    }
}
