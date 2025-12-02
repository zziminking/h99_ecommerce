package h99.ecommerce.service;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductStatistics;
import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.order.OrderItem;
import h99.ecommerce.domain.order.OrderStatus;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.cartitem.CartItem;
import h99.ecommerce.domain.point.Point;

import static org.assertj.core.api.Assertions.assertThat;

import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.coupon.CouponRepository;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import h99.ecommerce.domain.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
public class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long testCouponId;
    private Long[] testUserIds;

    @BeforeEach
    void setUp() {
        // 테스트용 쿠폰 생성 (최대 발급 수량 100개)
        testCouponId = transactionTemplate.execute(status -> {
            Coupon coupon = Coupon.builder()
                    .name("테스트 선착순 쿠폰")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .maxIssueCount(100)
                    .issuedCount(0)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();

            Coupon saved = couponRepository.save(coupon);
            return saved.getCouponId();
        });

        // 테스트용 사용자 101명 생성
        testUserIds = new Long[101];
        for (int i = 0; i < 101; i++) {
            final int index = i;
            testUserIds[i] = transactionTemplate.execute(status -> {
                User user = User.builder()
                        .username("테스트유저" + index)
                        .point(BigDecimal.valueOf(100000))
                        .build();

                User saved = userRepository.save(user);
                return saved.getUserId();
            });
        }
    }

    @Test
    @DisplayName("101명이 동시에 쿠폰 발급 - 선착순 100명만 성공")
    void concurrentCouponIssue_101Users_Only100Success() throws InterruptedException {
        // Given: 최대 발급 수량 100개 쿠폰
        int threadCount = 101;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 101명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(testUserIds[index], testCouponId);
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

        // Then: 100명 성공, 1명 실패
        Coupon coupon = couponRepository.findOne(testCouponId);

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(coupon.getIssuedCount()).isEqualTo(100);

        // 발급된 사용자 쿠폰 수 확인
        int totalUserCoupons = 0;
        for (Long userId : testUserIds) {
            totalUserCoupons += userCouponRepository.findByUserId(userId).size();
        }
        assertThat(totalUserCoupons).isEqualTo(100);

        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failCount.get() + "명");
        System.out.println("쿠폰 발급 수: " + coupon.getIssuedCount());
    }

    @Test
    @DisplayName("50명이 동시에 쿠폰 발급 - 수량 30개 쿠폰")
    void concurrentCouponIssue_50Users_30CouponsAvailable() throws InterruptedException {
        // Given: 최대 발급 수량 30개 쿠폰 생성
        Long limitedCouponId = transactionTemplate.execute(status -> {
            Coupon coupon = Coupon.builder()
                    .name("한정 쿠폰")
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(5000))
                    .maxIssueCount(30)
                    .issuedCount(0)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();

            return couponRepository.save(coupon).getCouponId();
        });

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 50명이 동시에 30개 한정 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(testUserIds[index], limitedCouponId);
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

        // Then: 30명만 성공, 20명 실패
        Coupon coupon = couponRepository.findOne(limitedCouponId);

        assertThat(successCount.get()).isEqualTo(30);
        assertThat(failCount.get()).isEqualTo(20);
        assertThat(coupon.getIssuedCount()).isEqualTo(30);

        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failCount.get() + "명");
        System.out.println("쿠폰 발급 수: " + coupon.getIssuedCount());
    }

    @Test
    @DisplayName("품절 쿠폰 발급 - 모두 실패")
    void concurrentCouponIssue_SoldOut_AllFail() throws InterruptedException {
        // Given: 이미 품절된 쿠폰 (issuedCount = maxIssueCount)
        Long soldOutCouponId = transactionTemplate.execute(status -> {
            Coupon coupon = Coupon.builder()
                    .name("품절 쿠폰")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(20))
                    .maxIssueCount(10)
                    .issuedCount(10)  // 이미 전부 발급됨
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();

            return couponRepository.save(coupon).getCouponId();
        });

        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 20명이 품절 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(testUserIds[index], soldOutCouponId);
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

        // Then: 모두 실패
        Coupon coupon = couponRepository.findOne(soldOutCouponId);

        assertThat(successCount.get()).isEqualTo(0);
        assertThat(failCount.get()).isEqualTo(20);
        assertThat(coupon.getIssuedCount()).isEqualTo(10);  // 발급 수 변화 없음

        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failCount.get() + "명");
        System.out.println("쿠폰 발급 수: " + coupon.getIssuedCount());
    }
}
