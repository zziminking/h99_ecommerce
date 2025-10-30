package h99.ecommerce.controller;

import h99.ecommerce.dto.CouponDto;
import h99.ecommerce.dto.UserCouponDto;
import h99.ecommerce.request.CouponIssueRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/coupons")
@Tag(name = "쿠폰", description = "쿠폰 관리 API")
public class CouponMockController {

    // Mock 데이터
    private static final List<CouponDto> MOCK_COUPONS = createMockCoupons();
    private static final List<UserCouponDto> MOCK_USER_COUPONS = new ArrayList<>();
    private static final AtomicInteger USER_COUPON_ID_GENERATOR = new AtomicInteger(1);

    // 쿠폰 목록 조회 (GET /api/coupons)
    @GetMapping
    @Operation(
            summary = "쿠폰 목록 조회",
            description = "발급 가능한 쿠폰 목록을 조회합니다."
    )
    public ResponseEntity<List<CouponDto>> getCoupons(
            @Parameter(description = "쿠폰 상태 필터", example = "ACTIVE")
            @RequestParam(required = false) String status
    ) {
        List<CouponDto> coupons = MOCK_COUPONS;

        // 상태 필터링
        if (status != null && !status.isEmpty()) {
            coupons = MOCK_COUPONS.stream()
                    .filter(c -> c.getStatus().equals(status))
                    .toList();
        }

        return ResponseEntity.ok(coupons);
    }

    // 쿠폰 조회 (GET / api/coupons/{couponId})
    @GetMapping("/{couponId}")
    @Operation(
            summary = "쿠폰 조회",
            description = "특정 쿠폰을 조회합니다."
    )
    public ResponseEntity<CouponDto> getCoupon(
            @Parameter(description = "쿠폰 ID", example = "1", required = true)
            @PathVariable Integer couponId
    ) {
        return ResponseEntity.ok(MOCK_COUPONS.stream()
                .filter(c -> c.getCouponId().equals(couponId))
                .findFirst()
                .orElse(null));
    }

    // 사용자 쿠폰 조회 (GET /api/coupons/users/{userId})
    @GetMapping("/users/{userId}")
    @Operation(
            summary = "사용자 쿠폰 조회",
            description = "특정 사용자가 보유한 쿠폰 목록을 조회합니다."
    )
    public ResponseEntity<List<UserCouponDto>> getUserCoupons(
            @Parameter(description = "사용자 ID", example = "1", required = true)
            @PathVariable Integer userId,

            @Parameter(description = "사용 여부 필터 (true: 사용됨, false: 사용 가능)", example = "false")
            @RequestParam(required = false) Boolean isUsed
    ) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        List<UserCouponDto> userCoupons = MOCK_USER_COUPONS.stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .toList();

        // 사용 여부 필터링
        if (isUsed != null) {
            userCoupons = userCoupons.stream()
                    .filter(uc -> uc.getIsUsed().equals(isUsed))
                    .toList();
        }

        return ResponseEntity.ok(userCoupons);
    }

    // 선착순 쿠폰 발급 (POST /api/coupons/issue)
    @PostMapping("/issue")
    @Operation(
            summary = "선착순 쿠폰 발급",
            description = "선착순으로 쿠폰을 발급합니다. 발급 수량이 초과되면 실패합니다."
    )
    public ResponseEntity<UserCouponDto> issueCoupon(@RequestBody CouponIssueRequest request) {
        // 쿠폰 발급
        UserCouponDto userCoupon = UserCouponDto.builder()
                .userCouponId(USER_COUPON_ID_GENERATOR.getAndIncrement())
                .userId(request.getUserId())
                .couponId(1)
                .couponName("선착순할인쿠폰")
                .isUsed(false)
                .usedAt(null)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(userCoupon);
    }

    // Mock 쿠폰 데이터 생성
    private static List<CouponDto> createMockCoupons() {
        List<CouponDto> coupons = new ArrayList<>();

        coupons.add(CouponDto.builder()
                .couponId(1)
                .name("신규가입 10% 할인 쿠폰")
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))
                .maxIssueCount(100)
                .issuedCount(45)
                .startAt(LocalDateTime.of(2025, 10, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
                .status("ACTIVE")
                .build());

        coupons.add(CouponDto.builder()
                .couponId(2)
                .name("5000원 할인 쿠폰")
                .discountType("FIXED")
                .discountValue(new BigDecimal("5000"))
                .maxIssueCount(50)
                .issuedCount(49)
                .startAt(LocalDateTime.of(2025, 10, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 11, 30, 23, 59))
                .status("ACTIVE")
                .build());

        coupons.add(CouponDto.builder()
                .couponId(3)
                .name("20% 할인 쿠폰 (종료됨)")
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("20"))
                .maxIssueCount(30)
                .issuedCount(30)
                .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
                .endAt(LocalDateTime.of(2025, 9, 30, 23, 59))
                .status("INACTIVE")
                .build());

        return coupons;
    }
}
