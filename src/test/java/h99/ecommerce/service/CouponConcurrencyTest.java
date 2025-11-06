package h99.ecommerce.service;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.CouponStatus;
import h99.ecommerce.domain.DiscountType;
import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.infrastructure.repository.InMemoryCouponRepository;
import h99.ecommerce.infrastructure.repository.InMemoryUserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CouponConcurrencyTest {

    private CouponService couponService;
    private InMemoryCouponRepository couponRepository;
    private InMemoryUserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
        userCouponRepository = new InMemoryUserCouponRepository();
        couponService = new CouponService(couponRepository, userCouponRepository);
    }

    @Test
    @DisplayName("동시에 100명이 100장 쿠폰 발급 시도 시 정확히 100명만 성공해야 함")
    void concurrent_coupon_issue_should_not_exceed_max_count() throws InterruptedException {
        // Given: 최대 100장 발급 가능한 쿠폰 생성
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                1,
                "선착순 100명 쿠폰",
                DiscountType.FIXED,
                new BigDecimal("5000"),
                100, // maxIssueCount
                0,   // issuedCount
                now.minusDays(1),
                now.plusDays(30),
                CouponStatus.ACTIVE,
                null,
                null
        );
        couponRepository.save(coupon);

        // When: 200명이 동시에 쿠폰 발급 시도
        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(userId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 100명만 성공해야 함
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Coupon finalCoupon = couponRepository.findOne(1);
        System.out.println("최종 발급 수량: " + finalCoupon.getIssuedCount());

        assertEquals(100, successCount.get(), "성공 횟수는 100이어야 합니다");
        assertEquals(100, failCount.get(), "실패 횟수는 100이어야 합니다");
        assertEquals(100, finalCoupon.getIssuedCount(), "최종 발급 수량은 100이어야 합니다");
    }

    @Test
    @DisplayName("동시에 1000명이 100장 쿠폰 발급 시도 시 정확히 100명만 성공해야 함")
    void concurrent_coupon_issue_with_high_contention() throws InterruptedException {
        // Given: 최대 100장 발급 가능한 쿠폰 생성
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                1,
                "선착순 100명 쿠폰",
                DiscountType.PERCENTAGE,
                new BigDecimal("10"),
                100, // maxIssueCount
                0,   // issuedCount
                now.minusDays(1),
                now.plusDays(30),
                CouponStatus.ACTIVE,
                null,
                null
        );
        couponRepository.save(coupon);

        // When: 1000명이 동시에 쿠폰 발급 시도
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(userId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 100명만 성공해야 함
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Coupon finalCoupon = couponRepository.findOne(1);
        System.out.println("최종 발급 수량: " + finalCoupon.getIssuedCount());

        assertEquals(100, successCount.get(), "성공 횟수는 100이어야 합니다");
        assertEquals(900, failCount.get(), "실패 횟수는 900이어야 합니다");
        assertEquals(100, finalCoupon.getIssuedCount(), "최종 발급 수량은 100이어야 합니다");
    }

    @Test
    @DisplayName("동시에 같은 사용자가 쿠폰 발급 시도 시 1번만 성공해야 함")
    void concurrent_same_user_should_issue_only_once() throws InterruptedException {
        // Given: 최대 100장 발급 가능한 쿠폰 생성
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                1,
                "선착순 100명 쿠폰",
                DiscountType.FIXED,
                new BigDecimal("3000"),
                100,
                0,
                now.minusDays(1),
                now.plusDays(30),
                CouponStatus.ACTIVE,
                null,
                null
        );
        couponRepository.save(coupon);

        // When: 같은 사용자(userId=1)가 100번 동시 발급 시도
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(1, 1); // 모두 userId=1
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 1번만 성공해야 함
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Coupon finalCoupon = couponRepository.findOne(1);
        System.out.println("최종 발급 수량: " + finalCoupon.getIssuedCount());

        assertEquals(1, successCount.get(), "같은 사용자는 1번만 성공해야 합니다");
        assertEquals(99, failCount.get(), "나머지는 중복 발급으로 실패해야 합니다");
        assertEquals(1, finalCoupon.getIssuedCount(), "최종 발급 수량은 1이어야 합니다");
    }

    @Test
    @DisplayName("동시에 10장 쿠폰 발급 완료 후 추가 발급 시도는 모두 실패해야 함")
    void concurrent_issue_after_sold_out() throws InterruptedException {
        // Given: 최대 10장만 발급 가능한 쿠폰
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                1,
                "선착순 10명 쿠폰",
                DiscountType.FIXED,
                new BigDecimal("1000"),
                10,
                0,
                now.minusDays(1),
                now.plusDays(30),
                CouponStatus.ACTIVE,
                null,
                null
        );
        couponRepository.save(coupon);

        // When: 50명이 동시에 쿠폰 발급 시도
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(userId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 10명만 성공
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Coupon finalCoupon = couponRepository.findOne(1);
        System.out.println("최종 발급 수량: " + finalCoupon.getIssuedCount());

        assertEquals(10, successCount.get(), "성공 횟수는 10이어야 합니다");
        assertEquals(40, failCount.get(), "실패 횟수는 40이어야 합니다");
        assertEquals(10, finalCoupon.getIssuedCount(), "최종 발급 수량은 10이어야 합니다");
    }
}
