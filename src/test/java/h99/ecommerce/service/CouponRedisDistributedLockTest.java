package h99.ecommerce.service;

import static org.assertj.core.api.Assertions.*;

import com.redis.testcontainers.RedisContainer;
import h99.ecommerce.domain.Coupon;
import h99.ecommerce.domain.CouponStatus;
import h99.ecommerce.domain.DiscountType;
import h99.ecommerce.domain.User;
import h99.ecommerce.repository.CouponRepository;
import h99.ecommerce.repository.UserCouponRepository;
import h99.ecommerce.repository.UserRepository;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
public class CouponRedisDistributedLockTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }

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
        testCouponId = transactionTemplate.execute(status -> {
            Coupon coupon = Coupon.builder()
                    .name("Redis 분산락 테스트 쿠폰")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .maxIssueCount(40)  // ← 40개
                    .issuedCount(0)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();

            return couponRepository.save(coupon).getCouponId();
        });

        testUserIds = new Long[100];
        for (int i = 0; i < 100; i++) {
            final int index = i;
            testUserIds[i] = transactionTemplate.execute(status -> {
                User user = User.builder()
                        .username("Redis테스트유저" + index)
                        .point(BigDecimal.valueOf(100000))
                        .build();

                return userRepository.save(user).getUserId();
            });
        }
    }

    @Test
    @DisplayName("쿠폰 40개 - 100명 동시 요청 테스트")
    void issue_coupon_40_user_100_concurrent_request() throws InterruptedException {
        int threadCount = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

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

        Coupon coupon = couponRepository.findOne(testCouponId);

        assertThat(successCount.get()).isEqualTo(40);
        assertThat(failCount.get()).isEqualTo(60);
        assertThat(coupon.getIssuedCount()).isEqualTo(40);

        int totalUserCoupons = 0;
        for (Long userId : testUserIds) {
            totalUserCoupons += userCouponRepository.findByUserId(userId).size();
        }
        assertThat(totalUserCoupons).isEqualTo(40);

        System.out.println("=== Redis 분산락 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failCount.get() + "명");
        System.out.println("쿠폰 발급 수: " + coupon.getIssuedCount());
        System.out.println("UserCoupon 총 개수: " + totalUserCoupons);
    }
}
