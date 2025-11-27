package h99.ecommerce.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import h99.ecommerce.domain.Point;
import h99.ecommerce.domain.User;
import h99.ecommerce.repository.PointRepository;
import h99.ecommerce.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
public class PointRedisDistributedLockTest {

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
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long testUserId;

    @Test
    @DisplayName("동일 사용자 포인트 충전 100회 요청")
    void charge_point_100_requests() throws InterruptedException {
        testUserId = transactionTemplate.execute(status -> {
            User user = User.builder().username("포인트충전테스트유저").point(BigDecimal.ZERO).build();
            return userRepository.save(user).getUserId();
        });

        int threadCount = 100;
        BigDecimal chargeAmount = BigDecimal.valueOf(500);

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentService.chargePoint(testUserId, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("충전 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        User user = userRepository.findOne(testUserId);
        List<Point> pointHistory = pointRepository.findByUserId(testUserId);

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(user.getPoint()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(pointHistory).hasSize(100);
        assertThat(pointHistory).allMatch(p -> p.getAmount().compareTo(BigDecimal.ZERO) > 0);

        System.out.println("=== 포인트 충전 동시성 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "건");
        System.out.println("실패: " + failCount.get() + "건");
        System.out.println("최종 잔액: " + user.getPoint());
        System.out.println("히스토리 개수: " + pointHistory.size());
    }

    @Test
    @DisplayName("포인트 차감 44회 동시 요청")
    void deduct_point_44_concurrent_requests() throws InterruptedException {
        testUserId = transactionTemplate.execute(status -> {
            User user = User.builder().username("포인트차감테스트").point(BigDecimal.valueOf(88000)).build();
            return userRepository.save(user).getUserId();
        });

        Long[] orderIds = new Long[44];
        for (int i = 0; i < 44; i++) {
            orderIds[i] = (long) (i + 1);
        }

        int threadCount = 44;
        BigDecimal payAmount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();


        for(int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    paymentService.processPayment(testUserId, orderIds[index], payAmount, null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("결제실패");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        User user = userRepository.findOne(testUserId);
        List<Point> pointHistory = pointRepository.findByUserId(testUserId);

        assertThat(successCount.get()).isEqualTo(44);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(user.getPoint()).isEqualByComparingTo(BigDecimal.valueOf(44000));

        assertThat(pointHistory).hasSize(44);
        assertThat(pointHistory).allMatch(p -> p.getAmount().compareTo(BigDecimal.ZERO) < 0);

        BigDecimal totalDeducted = pointHistory.stream()
                .map(Point::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDeducted).isEqualByComparingTo(BigDecimal.valueOf(-44000));

        System.out.println("=== 포인트 차감 동시성 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "건");
        System.out.println("실패: " + failCount.get() + "건");
        System.out.println("최종 잔액: " + user.getPoint());
        System.out.println("히스토리 개수: " + pointHistory.size());
    }
}
