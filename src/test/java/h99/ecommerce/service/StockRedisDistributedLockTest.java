package h99.ecommerce.service;

import static org.assertj.core.api.Assertions.*;

import com.redis.testcontainers.RedisContainer;
import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.ProductRepository;
import java.math.BigDecimal;
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
public class StockRedisDistributedLockTest {

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
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("동일 상품 재고 100개 - 100명 동시 1개씩 차감 - 최종 재고 0개")
    void deduct_stock_100_concurrent_requests_success() throws InterruptedException {
        Long testProductId = transactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .name("재고차감테스트상품")
                    .description("동시성 테스트용")
                    .price(BigDecimal.valueOf(10000))
                    .stock(new Stock(100))  // 초기 재고 100개
                    .totalViewCount(0)
                    .build();
            return productRepository.save(product).getProductId();
        });

        int threadCount = 100;
        int deductQuantity = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    productService.deductStock(testProductId, deductQuantity);
                    int currentSuccess = successCount.incrementAndGet();
                    if (currentSuccess % 10 == 0) {
                        System.out.println("성공 " + currentSuccess + "건 (스레드-" + threadNum + ")");
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("실패: " + e.getMessage() + " (스레드-" + threadNum + ")");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Product product = productRepository.findOne(testProductId);

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(product.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 50개 - 100명 동시 1개씩 차감 - 50건 성공, 50건 실패")
    void deduct_stock_with_insufficient_stock() throws InterruptedException {
        Long testProductId = transactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .name("재고부족테스트상품")
                    .description("재고 부족 테스트용")
                    .price(BigDecimal.valueOf(10000))
                    .stock(new Stock(50))  // 초기 재고 50개
                    .totalViewCount(0)
                    .build();
            return productRepository.save(product).getProductId();
        });

        int threadCount = 100;
        int deductQuantity = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productService.deductStock(testProductId, deductQuantity);
                    successCount.incrementAndGet();
                } catch (NotEnoughStockException e) {
                    // 재고 부족 예외 발생 예상
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("예상치 못한 예외: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Product product = productRepository.findOne(testProductId);

        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);
        assertThat(product.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 1000개 - 100명 동시 10개씩 차감 - 최종 재고 0개")
    void deduct_stock_large_quantity_concurrent() throws InterruptedException {
        Long testProductId = transactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .name("대량재고차감테스트상품")
                    .description("대량 차감 테스트용")
                    .price(BigDecimal.valueOf(10000))
                    .stock(new Stock(1000))  // 초기 재고 1000개
                    .totalViewCount(0)
                    .build();
            return productRepository.save(product).getProductId();
        });

        int threadCount = 100;
        int deductQuantity = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productService.deductStock(testProductId, deductQuantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("재고 차감 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Product product = productRepository.findOne(testProductId);

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(product.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("3개 상품 각각 재고 100개 - 각 상품마다 100명 동시 차감 - 락 독립성 검증")
    void deduct_stock_multiple_products_concurrent() throws InterruptedException {
        Long[] productIds = new Long[3];
        for (int i = 0; i < 3; i++) {
            final int index = i;
            productIds[i] = transactionTemplate.execute(status -> {
                Product product = Product.builder()
                        .name("다중상품테스트" + index)
                        .description("상품 " + index)
                        .price(BigDecimal.valueOf(10000))
                        .stock(new Stock(100))  // 각 상품 재고 100개
                        .totalViewCount(0)
                        .build();
                return productRepository.save(product).getProductId();
            });
        }

        int threadCountPerProduct = 100;
        int totalThreadCount = threadCountPerProduct * 3;  // 300개 스레드

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(totalThreadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int productIndex = 0; productIndex < 3; productIndex++) {
            final Long productId = productIds[productIndex];

            for (int i = 0; i < threadCountPerProduct; i++) {
                executorService.submit(() -> {
                    try {
                        productService.deductStock(productId, 1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        System.err.println("재고 차감 실패: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(300);
        assertThat(failCount.get()).isEqualTo(0);

        for (Long productId : productIds) {
            Product product = productRepository.findOne(productId);
            assertThat(product.getStock().getQuantity()).isEqualTo(0);
        }

    }
}
