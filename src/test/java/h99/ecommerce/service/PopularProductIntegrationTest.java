package h99.ecommerce.service;

import com.redis.testcontainers.RedisContainer;
import h99.ecommerce.domain.product.PopularProductRepository;
import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductRepository;
import h99.ecommerce.domain.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PopularProductIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private PopularProductRepository popularProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private List<Long> testProductIds;
    private static final String PERIOD_3DAYS = "3days";
    private static final String PERIOD_7DAYS = "7days";

    @BeforeEach
    void setUp() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔧 테스트 환경 초기화");
        System.out.println("=".repeat(80));

        // 0. Redis 데이터 초기화 (이전 테스트 데이터 제거)
        if (popularProductRepository instanceof h99.ecommerce.infrastructure.repository.redis.RedisPopularProductRepository) {
            ((h99.ecommerce.infrastructure.repository.redis.RedisPopularProductRepository) popularProductRepository)
                .clearAll(PERIOD_3DAYS);
            ((h99.ecommerce.infrastructure.repository.redis.RedisPopularProductRepository) popularProductRepository)
                .clearAll(PERIOD_7DAYS);
        }
        System.out.println("✅ Redis 데이터 초기화 완료");

        // 1. 테스트 상품 10개 생성
        testProductIds = createTestProducts();
        System.out.println("✅ 테스트 상품 10개 생성 완료: " + testProductIds);

        // 2. Redis에 더미 인기상품 데이터 설정
        setupDummyPopularProducts();
        System.out.println("✅ Redis 더미 데이터 설정 완료");

        // 3. 초기 상태 출력
        printRedisData();
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    @DisplayName("상품 조회 시 Redis score +1 증가 확인")
    void increaseScore_when_product_viewed() {
        // given
        Long productId = testProductIds.get(0);  // 1등 상품
        Double initialScore = popularProductRepository.getScore(PERIOD_3DAYS, productId);
        System.out.println("\n📊 초기 점수: " + initialScore);

        // when - 상품 조회 5회 (트랜잭션 내에서 실행)
        System.out.println("👀 상품 조회 5회 실행...");
        for (int i = 0; i < 5; i++) {
            transactionTemplate.execute(status -> {
                productService.getProductWithViewCount(productId);
                return null;
            });
        }

        // 비동기 처리 대기
        sleep(1000);

        // then
        Double finalScore = popularProductRepository.getScore(PERIOD_3DAYS, productId);
        System.out.println("📊 최종 점수: " + finalScore);
        System.out.println("✅ 점수 증가: " + (finalScore - initialScore));

        assertThat(finalScore).isEqualTo(initialScore + 5.0);
    }

    @Test
    @DisplayName("상품 구매(주문) 시 Redis score +15 증가 확인 (3개 * 5점)")
    void increaseScore_when_product_ordered() {
        // given
        Long productId = testProductIds.get(0);  // 1등 상품
        Double initialScore3days = popularProductRepository.getScore(PERIOD_3DAYS, productId);
        Double initialScore7days = popularProductRepository.getScore(PERIOD_7DAYS, productId);

        System.out.println("\n📊 초기 점수 (3일): " + initialScore3days);
        System.out.println("📊 초기 점수 (7일): " + initialScore7days);

        // when - 주문 통계 업데이트 (주문 3개) - 트랜잭션 내에서 실행
        System.out.println("🛒 주문 3건 발생...");
        int orderQuantity = 3;
        transactionTemplate.execute(status -> {
            productService.updateOrderStatistics(productId, orderQuantity);
            return null;
        });

        // then
        Double finalScore3days = popularProductRepository.getScore(PERIOD_3DAYS, productId);
        Double finalScore7days = popularProductRepository.getScore(PERIOD_7DAYS, productId);

        System.out.println("📊 최종 점수 (3일): " + finalScore3days);
        System.out.println("📊 최종 점수 (7일): " + finalScore7days);
        System.out.println("✅ 점수 증가 (3일): " + (finalScore3days - initialScore3days));
        System.out.println("✅ 점수 증가 (7일): " + (finalScore7days - initialScore7days));

        // 주문 3개 = 15점 증가 (주문 1건당 +5점)
        assertThat(finalScore3days).isEqualTo(initialScore3days + 15.0);
        assertThat(finalScore7days).isEqualTo(initialScore7days + 15.0);
    }

    @Test
    @DisplayName("인기상품 조회 API - 상위 5개 반환 (3일)")
    void getPopularProducts_returns_top5_for_3days() throws Exception {
        // given
        System.out.println("\n🔍 인기상품 API 호출 (3일)");

        // when & then
        mockMvc.perform(get("/api/products/popular/3days")
                        .param("limit", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].productId").value(testProductIds.get(0)))  // 1등
                .andExpect(jsonPath("$[1].productId").value(testProductIds.get(1)))  // 2등
                .andExpect(jsonPath("$[2].productId").value(testProductIds.get(2)))  // 3등
                .andExpect(jsonPath("$[3].productId").value(testProductIds.get(3)))  // 4등
                .andExpect(jsonPath("$[4].productId").value(testProductIds.get(4))); // 5등

        System.out.println("✅ 상위 5개 상품 조회 성공");
    }

    @Test
    @DisplayName("인기상품 조회 API - 상위 5개 반환 (7일)")
    void getPopularProducts_returns_top5_for_7days() throws Exception {
        // given
        System.out.println("\n🔍 인기상품 API 호출 (7일)");

        // when & then
        mockMvc.perform(get("/api/products/popular/7days")
                        .param("limit", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].productId").value(testProductIds.get(0)))  // 1등
                .andExpect(jsonPath("$[1].productId").value(testProductIds.get(1)))  // 2등
                .andExpect(jsonPath("$[2].productId").value(testProductIds.get(2)))  // 3등
                .andExpect(jsonPath("$[3].productId").value(testProductIds.get(3)))  // 4등
                .andExpect(jsonPath("$[4].productId").value(testProductIds.get(4))); // 5등

        System.out.println("✅ 상위 5개 상품 조회 성공");
    }

    @Test
    @DisplayName("인기상품 조회 API - 기본 limit 5 적용")
    void getPopularProducts_default_limit() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products/popular/3days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        System.out.println("✅ 기본 limit 5 적용 확인");
    }

    @Test
    @DisplayName("인기상품 조회 API - limit 3으로 상위 3개만 반환")
    void getPopularProducts_returns_top3() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products/popular/3days")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].productId").value(testProductIds.get(0)))
                .andExpect(jsonPath("$[1].productId").value(testProductIds.get(1)))
                .andExpect(jsonPath("$[2].productId").value(testProductIds.get(2)));

        System.out.println("✅ 상위 3개 상품 조회 성공");
    }

    @Test
    @DisplayName("실시간 업데이트 통합 시나리오 - 조회와 주문이 순위에 반영")
    void integrated_scenario_view_and_order_updates_ranking() {
        // given - 초기 순위
        System.out.println("\n📊 초기 순위:");
        printTop5();

        // when - 5등 상품에 주문 100건 발생 (500점 증가 → 100 + 500 = 600점)
        Long productId5th = testProductIds.get(4);  // 5등 (100점)
        System.out.println("\n🚀 5등 상품(" + productId5th + ")에 주문 100건 발생!");

        for (int i = 0; i < 100; i++) {
            final int index = i;
            transactionTemplate.execute(status -> {
                productService.updateOrderStatistics(productId5th, 1);
                return null;
            });
        }

        // then - 5등 상품이 1등으로 상승
        System.out.println("\n📊 최종 순위:");
        printTop5();

        List<Long> top5 = popularProductRepository.getTopProducts(PERIOD_3DAYS, 5);
        assertThat(top5.get(0)).isEqualTo(productId5th);  // 1등으로 상승
        System.out.println("✅ 5등 → 1등 순위 변동 확인");
    }

    @Test
    @DisplayName("여러 상품 동시 조회/주문 시 score 정확히 반영")
    void concurrent_updates_reflect_scores_correctly() {
        // given
        Long product1 = testProductIds.get(0);
        Long product2 = testProductIds.get(1);

        Double initialScore1 = popularProductRepository.getScore(PERIOD_3DAYS, product1);
        Double initialScore2 = popularProductRepository.getScore(PERIOD_3DAYS, product2);

        System.out.println("\n📊 초기 점수:");
        System.out.println("  상품1(" + product1 + "): " + initialScore1);
        System.out.println("  상품2(" + product2 + "): " + initialScore2);

        // when - 상품1: 조회 10회, 상품2: 주문 2건
        System.out.println("\n🔄 업데이트:");
        System.out.println("  상품1: 조회 10회 (+10점)");
        System.out.println("  상품2: 주문 2건 (+10점)");

        for (int i = 0; i < 10; i++) {
            transactionTemplate.execute(status -> {
                productService.getProductWithViewCount(product1);
                return null;
            });
        }

        transactionTemplate.execute(status -> {
            productService.updateOrderStatistics(product2, 2);
            return null;
        });

        sleep(1000);

        // then
        Double finalScore1 = popularProductRepository.getScore(PERIOD_3DAYS, product1);
        Double finalScore2 = popularProductRepository.getScore(PERIOD_3DAYS, product2);

        System.out.println("\n📊 최종 점수:");
        System.out.println("  상품1(" + product1 + "): " + finalScore1 + " (+" + (finalScore1 - initialScore1) + ")");
        System.out.println("  상품2(" + product2 + "): " + finalScore2 + " (+" + (finalScore2 - initialScore2) + ")");

        assertThat(finalScore1 - initialScore1).isEqualTo(10.0);
        assertThat(finalScore2 - initialScore2).isEqualTo(10.0);
        System.out.println("✅ 점수 증가 정확히 반영됨");
    }

    // ========== Helper Methods ==========

    private List<Long> createTestProducts() {
        return transactionTemplate.execute(status -> {
            List<Long> productIds = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                Product product = Product.builder()
                        .name("테스트상품" + i)
                        .description("설명" + i)
                        .price(BigDecimal.valueOf(10000 * i))
                        .stock(new Stock(100))
                        .totalViewCount(0)
                        .build();
                Product saved = productRepository.save(product);
                productIds.add(saved.getProductId());
            }
            return productIds;
        });
    }

    private void setupDummyPopularProducts() {
        // 3일간 인기상품 데이터 (점수 높은 순으로 설정)
        Map<Long, Double> scores3days = new HashMap<>();
        scores3days.put(testProductIds.get(0), 500.0);  // 1등
        scores3days.put(testProductIds.get(1), 400.0);  // 2등
        scores3days.put(testProductIds.get(2), 300.0);  // 3등
        scores3days.put(testProductIds.get(3), 200.0);  // 4등
        scores3days.put(testProductIds.get(4), 100.0);  // 5등
        scores3days.put(testProductIds.get(5), 90.0);   // 6등
        scores3days.put(testProductIds.get(6), 80.0);   // 7등
        scores3days.put(testProductIds.get(7), 70.0);   // 8등
        scores3days.put(testProductIds.get(8), 60.0);   // 9등
        scores3days.put(testProductIds.get(9), 50.0);   // 10등

        popularProductRepository.saveAll(PERIOD_3DAYS, scores3days);

        // 7일간 인기상품 데이터 (3일 데이터와 동일하게 설정)
        Map<Long, Double> scores7days = new HashMap<>();
        scores7days.put(testProductIds.get(0), 1000.0);  // 1등
        scores7days.put(testProductIds.get(1), 900.0);   // 2등
        scores7days.put(testProductIds.get(2), 800.0);   // 3등
        scores7days.put(testProductIds.get(3), 700.0);   // 4등
        scores7days.put(testProductIds.get(4), 600.0);   // 5등
        scores7days.put(testProductIds.get(5), 500.0);   // 6등
        scores7days.put(testProductIds.get(6), 400.0);   // 7등
        scores7days.put(testProductIds.get(7), 300.0);   // 8등
        scores7days.put(testProductIds.get(8), 200.0);   // 9등
        scores7days.put(testProductIds.get(9), 100.0);   // 10등

        popularProductRepository.saveAll(PERIOD_7DAYS, scores7days);

        // TTL 설정
        popularProductRepository.setExpiration(PERIOD_3DAYS, 345600);  // 4일
        popularProductRepository.setExpiration(PERIOD_7DAYS, 691200);  // 8일
    }

    private void printRedisData() {
        System.out.println("\n📊 Redis 더미 데이터 (3일):");
        List<Long> top5_3days = popularProductRepository.getTopProducts(PERIOD_3DAYS, 5);
        for (int i = 0; i < top5_3days.size(); i++) {
            Long productId = top5_3days.get(i);
            Double score = popularProductRepository.getScore(PERIOD_3DAYS, productId);
            System.out.printf("  %d위: Product#%d (%.1f점)\n", i + 1, productId, score);
        }

        System.out.println("\n📊 Redis 더미 데이터 (7일):");
        List<Long> top5_7days = popularProductRepository.getTopProducts(PERIOD_7DAYS, 5);
        for (int i = 0; i < top5_7days.size(); i++) {
            Long productId = top5_7days.get(i);
            Double score = popularProductRepository.getScore(PERIOD_7DAYS, productId);
            System.out.printf("  %d위: Product#%d (%.1f점)\n", i + 1, productId, score);
        }
    }

    private void printTop5() {
        List<Long> top5 = popularProductRepository.getTopProducts(PERIOD_3DAYS, 5);
        for (int i = 0; i < top5.size(); i++) {
            Long productId = top5.get(i);
            Double score = popularProductRepository.getScore(PERIOD_3DAYS, productId);
            System.out.printf("  %d위: Product#%d (%.1f점)\n", i + 1, productId, score);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
