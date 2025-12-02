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

import com.redis.testcontainers.RedisContainer;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.domain.product.ProductRepository;
import h99.ecommerce.domain.product.ProductStatisticsRepository;
import java.math.BigDecimal;
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

@SpringBootTest
@Testcontainers
public class ProductCacheIntegrationTest {

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
    private CacheManager cacheManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long testProductId;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        cacheManager.getCacheNames().forEach(cacheName ->
            cacheManager.getCache(cacheName).clear()
        );

        // 테스트 상품 생성
        testProductId = transactionTemplate.execute(status -> {
            Product product = new Product(
                null,
                "캐시 테스트 상품",
                "설명",
                new BigDecimal("10000"),
                new Stock(100),
                0,
                null,
                null
            );
            return productRepository.save(product).getProductId();
        });
    }

    @Test
    @DisplayName("상품 조회 캐싱 테스트 - 2차 조회 시 DB 조회 안 함")
    void product_caching_test() {
        // given: 캐시가 비어있음
        String cacheKey = "cache:product:detail:id:" + testProductId;
        assertThat(cacheManager.getCache("product").get(cacheKey)).isNull();

        // when: 1차 조회 (캐시 미스)
        Product firstCall = productService.getProduct(testProductId);

        // then: 캐시에 저장됨
        assertThat(firstCall).isNotNull();
        assertThat(cacheManager.getCache("product").get(cacheKey)).isNotNull();

        // when: 2차 조회 (캐시 히트)
        Product secondCall = productService.getProduct(testProductId);

        // then: 동일한 객체 (캐시에서 가져옴)
        assertThat(secondCall).isNotNull();
        assertThat(secondCall.getProductId()).isEqualTo(firstCall.getProductId());
        assertThat(secondCall.getName()).isEqualTo(firstCall.getName());

        System.out.println("=== 캐싱 테스트 결과 ===");
        System.out.println("1차 조회: " + firstCall.getName());
        System.out.println("2차 조회: " + secondCall.getName());
        System.out.println("캐시 키: " + cacheKey);
        System.out.println("캐시 저장 확인: " + (cacheManager.getCache("product").get(cacheKey) != null));
    }

    @Test
    @DisplayName("재고 차감 시 캐시 무효화 테스트")
    void cache_eviction_on_stock_deduction() {
        // given: 캐시에 상품 저장
        Product cached = productService.getProduct(testProductId);
        String cacheKey = "cache:product:detail:id:" + testProductId;
        assertThat(cacheManager.getCache("product").get(cacheKey)).isNotNull();

        // when: 재고 차감 (캐시 무효화 트리거)
        productService.deductStock(testProductId, 10);

        // then: 캐시가 삭제됨
        assertThat(cacheManager.getCache("product").get(cacheKey)).isNull();

        // when: 재조회 (캐시 미스 -> DB 조회)
        Product afterDeduction = productService.getProduct(testProductId);

        // then: 최신 데이터 (재고 차감 반영)
        assertThat(afterDeduction.getStock().getQuantity()).isEqualTo(90);

        System.out.println("=== 캐시 무효화 테스트 결과 ===");
        System.out.println("재고 차감 전 캐시: 있음");
        System.out.println("재고 차감 후 캐시: 없음");
        System.out.println("재조회 후 재고: " + afterDeduction.getStock().getQuantity());
    }

    @Test
    @DisplayName("인기 상품 캐싱 테스트")
    void popular_products_caching_test() {
        // given
        String cacheKey = "cache:product:popular:view:5";

        // when: 1차 조회
        var firstCall = productService.getPopularProductsByViewCount(5);
        assertThat(cacheManager.getCache("popularProductsByView").get(cacheKey)).isNotNull();

        // when: 2차 조회 (캐시 히트)
        var secondCall = productService.getPopularProductsByViewCount(5);

        // then: 캐시에서 반환
        assertThat(secondCall).isNotNull();

        System.out.println("=== 인기 상품 캐싱 테스트 결과 ===");
        System.out.println("1차 조회 결과 수: " + firstCall.size());
        System.out.println("2차 조회 결과 수: " + secondCall.size());
        System.out.println("캐시 키: " + cacheKey);
    }
}
