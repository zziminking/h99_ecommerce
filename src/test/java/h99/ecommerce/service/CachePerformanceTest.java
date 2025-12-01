package h99.ecommerce.service;

import com.redis.testcontainers.RedisContainer;
import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.ProductStatistics;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.repository.ProductRepository;
import h99.ecommerce.repository.ProductStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class CachePerformanceTest {

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
    private ProductStatisticsRepository productStatisticsRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CacheManager cacheManager;

    private static final int TOTAL_PRODUCTS = 100_000;  // 10만 건으로 테스트
    private static final int BATCH_SIZE = 1000;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        cacheManager.getCacheNames().forEach(cacheName -> 
            cacheManager.getCache(cacheName).clear()
        );
    }

    @Test
    @DisplayName("100만 건 더미 데이터 생성 및 캐시 성능 테스트")
    void cache_performance_test_with_1million_products() {
        System.out.println("\n========================================");
        System.out.println("캐시 성능 테스트 시작");
        System.out.println("========================================");
        System.out.println("테스트 데이터: " + TOTAL_PRODUCTS + " 건");
        System.out.println("========================================\n");

        createDummyData();

        createStatisticsData();

        testPopularProductsPerformance();

        testProductDetailPerformance();
    }

    /**
     * 10만 건 더미 상품 데이터 생성
     */
    private void createDummyData() {
        Random random = new Random();
        int batchCount = TOTAL_PRODUCTS / BATCH_SIZE;

        for (int i = 0; i < batchCount; i++) {
            final int batchIndex = i;
            transactionTemplate.execute(status -> {
                for (int j = 0; j < BATCH_SIZE; j++) {
                    int productIndex = batchIndex * BATCH_SIZE + j;
                    
                    Product product = Product.builder()
                            .name("상품_" + productIndex)
                            .description("더미 상품 설명 " + productIndex)
                            .price(BigDecimal.valueOf(1000 + random.nextInt(99000)))
                            .stock(new Stock(random.nextInt(1000)))
                            .totalViewCount(random.nextInt(10000))
                            .build();
                    
                    productRepository.save(product);
                }
                return null;
            });

            if ((i + 1) % 10 == 0) {
                System.out.println("   진행률: " + ((i + 1) * BATCH_SIZE) + " / " + TOTAL_PRODUCTS + " (" + 
                    String.format("%.1f", ((i + 1) * 100.0 / batchCount)) + "%)");
            }
        }
    }

    /**
     * 통계 데이터 생성 (최근 3일간)
     */
    private void createStatisticsData() {
        Random random = new Random();
        LocalDate today = LocalDate.now();
        
        // 최근 3일간 통계 생성
        for (int day = 0; day < 3; day++) {
            LocalDate date = today.minusDays(day);
            
            // 상위 1000개 상품에 대해서만 통계 생성 (성능 고려)
            transactionTemplate.execute(status -> {
                for (long productId = 1; productId <= 1000; productId++) {
                    ProductStatistics stats = ProductStatistics.builder()
                            .productId(productId)
                            .statisticsDate(date)
                            .viewCount(random.nextInt(1000))
                            .orderCount(random.nextInt(100))
                            .build();
                    
                    productStatisticsRepository.save(stats);
                }
                return null;
            });
            
            System.out.println("   " + date + " 통계 생성 완료");
        }
    }

    /**
     * 인기 상품 조회 성능 테스트
     */
    private void testPopularProductsPerformance() {
        int limit = 10;

        System.out.println("🔍 첫 번째 조회 (Cache Miss - DB 조회)");
        long firstCallStart = System.currentTimeMillis();
        List<Product> firstResult = productService.getPopularProductsByViewCount(limit);
        long firstCallTime = System.currentTimeMillis() - firstCallStart;
        
        System.out.println("   ⏱️  소요 시간: " + firstCallTime + "ms");
        System.out.println("   📦 조회 결과: " + firstResult.size() + "개\n");

        // 두 번째 조회 (Cache Hit)
        System.out.println("🔍 두 번째 조회 (Cache Hit - Redis 조회)");
        long secondCallStart = System.currentTimeMillis();
        List<Product> secondResult = productService.getPopularProductsByViewCount(limit);
        long secondCallTime = System.currentTimeMillis() - secondCallStart;
        
        System.out.println("   ⏱️  소요 시간: " + secondCallTime + "ms");
        System.out.println("   📦 조회 결과: " + secondResult.size() + "개\n");

        // 성능 개선율 계산
        double improvement = (double) firstCallTime / secondCallTime;
        
        System.out.println("📈 성능 개선 결과:");
        System.out.println("   Before (DB): " + firstCallTime + "ms");
        System.out.println("   After (Cache): " + secondCallTime + "ms");
        System.out.println("   개선율: " + String.format("%.1f", improvement) + "배");
        System.out.println("   개선률: " + String.format("%.1f", (1 - (double)secondCallTime/firstCallTime) * 100) + "%\n");

        // 검증
        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(secondCallTime).isLessThan(firstCallTime);
        
        System.out.println("✅ 캐시 히트 확인: 동일한 결과 반환");
        System.out.println("✅ 성능 개선 확인: Cache Hit가 더 빠름\n");
    }

    /**
     * 상품 상세 조회 성능 테스트
     */
    private void testProductDetailPerformance() {
        System.out.println("\n========================================");
        System.out.println("📦 상품 상세 조회 성능 테스트");
        System.out.println("========================================\n");

        Long productId = 1L;

        // 첫 번째 조회 (Cache Miss)
        System.out.println("🔍 첫 번째 조회 (Cache Miss - DB 조회)");
        long firstCallStart = System.currentTimeMillis();
        Product firstResult = productService.getProduct(productId);
        long firstCallTime = System.currentTimeMillis() - firstCallStart;
        
        System.out.println("   ⏱️  소요 시간: " + firstCallTime + "ms");
        System.out.println("   📦 상품명: " + firstResult.getName() + "\n");

        // 두 번째 조회 (Cache Hit)
        System.out.println("🔍 두 번째 조회 (Cache Hit - Redis 조회)");
        long secondCallStart = System.currentTimeMillis();
        Product secondResult = productService.getProduct(productId);
        long secondCallTime = System.currentTimeMillis() - secondCallStart;
        
        System.out.println("   ⏱️  소요 시간: " + secondCallTime + "ms");
        System.out.println("   📦 상품명: " + secondResult.getName() + "\n");

        // 10번 연속 조회 (모두 Cache Hit)
        System.out.println("🔍 10번 연속 조회 (모두 Cache Hit)");
        long multipleCallsStart = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            productService.getProduct(productId);
        }
        long multipleCallsTime = System.currentTimeMillis() - multipleCallsStart;
        long avgCacheHitTime = multipleCallsTime / 10;
        
        System.out.println("   ⏱️  총 소요 시간: " + multipleCallsTime + "ms");
        System.out.println("   ⏱️  평균 소요 시간: " + avgCacheHitTime + "ms\n");

        // 성능 개선율 계산
        double improvement = (double) firstCallTime / secondCallTime;
        
        System.out.println("📈 성능 개선 결과:");
        System.out.println("   Before (DB): " + firstCallTime + "ms");
        System.out.println("   After (Cache): " + secondCallTime + "ms");
        System.out.println("   평균 (Cache): " + avgCacheHitTime + "ms");
        System.out.println("   개선율: " + String.format("%.1f", improvement) + "배");
        System.out.println("   개선률: " + String.format("%.1f", (1 - (double)secondCallTime/firstCallTime) * 100) + "%\n");

        // 검증
        assertThat(secondResult.getProductId()).isEqualTo(firstResult.getProductId());
        assertThat(secondCallTime).isLessThan(firstCallTime);
    }
}
