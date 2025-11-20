package h99.ecommerce.service;

import static org.assertj.core.api.Assertions.assertThat;

import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.repository.ProductRepository;
import java.math.BigDecimal;
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
public class ProductConcurrencyTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long testProductId;

    @BeforeEach
    void setUp() {
        testProductId = transactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(BigDecimal.valueOf(10000))
                    .stock(new Stock(100))
                    .build();

            Product saved = productRepository.save(product);
            return saved.getProductId();
        });
    }

    @Test
    @DisplayName("101명이 동시에 1개씩 주문 - 재고 차감 동시성 테스트")
    void concurrentStockDeduction_100Users_1ItemEach() throws InterruptedException {
        // Given
        int threadCount = 101;
        int quantityPerThread = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productService.deductStock(testProductId, quantityPerThread);
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

        // Then
        Product product = productRepository.findOne(testProductId);

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(product.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("50명이 동시에 3개씩 주문")
    void concurrentStockDeduction_50Users_3ItemsEach_PreventOverselling() throws InterruptedException {
        // Given
        int threadCount = 50;
        int quantityPerThread = 3;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productService.deductStock(testProductId, quantityPerThread);
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

        // Then
        Product product = productRepository.findOne(testProductId);
        int finalStock = product.getStock().getQuantity();

        // 성공한 주문 수 * 3 = 차감된 재고
        int expectedDeducted = successCount.get() * quantityPerThread;
        assertThat(100 - finalStock).isEqualTo(expectedDeducted);
    }

    @Test
    @DisplayName("10명이 동시에 15개씩 주문 - 재시도 로직 테스트")
    void concurrentStockDeduction_10Users_15ItemsEach_RetryLogic() throws InterruptedException {
        // Given: 재고 100개
        int threadCount = 10;
        int quantityPerThread = 15;

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10명이 동시에 15개씩 주문
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productService.deductStock(testProductId, quantityPerThread);
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

        // Then: 6명 성공 (90개 차감), 4명 실패
        Product product = productRepository.findOne(testProductId);
        int finalStock = product.getStock().getQuantity();

        assertThat(successCount.get()).isEqualTo(6);  // 100 / 15 = 6명만 가능
        assertThat(failCount.get()).isEqualTo(4);
        assertThat(finalStock).isEqualTo(10);  // 100 - (6 * 15) = 10
    }
}
