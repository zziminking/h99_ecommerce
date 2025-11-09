package h99.ecommerce.service;

import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.ProductStatistics;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.infrastructure.repository.InMemoryProductRepository;
import h99.ecommerce.infrastructure.repository.InMemoryProductStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("상품 인기도 통합 테스트")
class ProductPopularityIntegrationTest {

    private ProductService productService;
    private InMemoryProductRepository productRepository;
    private InMemoryProductStatisticsRepository statisticsRepository;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        statisticsRepository = new InMemoryProductStatisticsRepository();
        productService = new ProductService(productRepository, statisticsRepository);

        // 테스트용 상품 10개 생성
        for (int i = 1; i <= 10; i++) {
            Product product = Product.builder()
                    .productId(i)
                    .name("상품 " + i)
                    .description("설명 " + i)
                    .price(new BigDecimal(i * 1000))
                    .stock(new Stock(100))
                    .totalViewCount(0)
                    .build();
            productRepository.save(product);
        }
    }

    @Test
    @DisplayName("상품 조회 시 일별 조회수 통계가 업데이트되어야 함")
    void product_view_should_update_daily_statistics() {
        // When: 상품 1번을 5번 조회
        for (int i = 0; i < 5; i++) {
            productService.getProduct(1);
        }

        // Then: 오늘 날짜의 상품 1번 조회수 통계가 5여야 함
        LocalDate today = LocalDate.now();
        ProductStatistics stats = statisticsRepository.findByProductIdAndDate(1, today).orElse(null);

        assertNotNull(stats, "통계가 생성되어야 함");
        assertEquals(1, stats.getProductId());
        assertEquals(today, stats.getStatisticsDate());
        assertEquals(5, stats.getViewCount());
        assertEquals(0, stats.getOrderCount());
    }

    @Test
    @DisplayName("주문 시 일별 주문 수량 통계가 업데이트되어야 함")
    void order_should_update_daily_statistics() {
        // When: 상품 1번을 3개 주문
        productService.updateOrderStatistics(1, 3);

        // Then: 오늘 날짜의 상품 1번 주문 수량 통계가 3이어야 함
        LocalDate today = LocalDate.now();
        ProductStatistics stats = statisticsRepository.findByProductIdAndDate(1, today).orElse(null);

        assertNotNull(stats, "통계가 생성되어야 함");
        assertEquals(1, stats.getProductId());
        assertEquals(today, stats.getStatisticsDate());
        assertEquals(0, stats.getViewCount());
        assertEquals(3, stats.getOrderCount());
    }

    @Test
    @DisplayName("조회수 기준 인기 상품 조회 - 최근 3일간 통계 합산")
    void get_popular_products_by_view_count() {
        // Given: 최근 3일간의 통계 데이터 생성
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        // 상품 1: 오늘 100회, 어제 50회, 그저께 30회 조회 (총 180회)
        createStatistics(1, today, 100, 0);
        createStatistics(1, yesterday, 50, 0);
        createStatistics(1, twoDaysAgo, 30, 0);

        // 상품 2: 오늘 80회, 어제 80회, 그저께 60회 조회 (총 220회)
        createStatistics(2, today, 80, 0);
        createStatistics(2, yesterday, 80, 0);
        createStatistics(2, twoDaysAgo, 60, 0);

        // 상품 3: 오늘 50회, 어제 40회, 그저께 20회 조회 (총 110회)
        createStatistics(3, today, 50, 0);
        createStatistics(3, yesterday, 40, 0);
        createStatistics(3, twoDaysAgo, 20, 0);

        // 상품 4: 4일 전에만 조회 (포함 안 됨)
        createStatistics(4, today.minusDays(3), 1000, 0);

        // When: 상위 3개 인기 상품 조회
        List<Product> popularProducts = productService.getPopularProductsByViewCount(3);

        // Then: 조회수 순으로 정렬되어야 함 (상품2 > 상품1 > 상품3)
        assertEquals(3, popularProducts.size());
        assertEquals(2, popularProducts.get(0).getProductId(), "1위는 상품 2 (220회)");
        assertEquals(1, popularProducts.get(1).getProductId(), "2위는 상품 1 (180회)");
        assertEquals(3, popularProducts.get(2).getProductId(), "3위는 상품 3 (110회)");
    }

    @Test
    @DisplayName("주문 수량 기준 인기 상품 조회 - 최근 3일간 통계 합산")
    void get_popular_products_by_order_count() {
        // Given: 최근 3일간의 통계 데이터 생성
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        // 상품 1: 오늘 10개, 어제 5개, 그저께 3개 주문 (총 18개)
        createStatistics(1, today, 0, 10);
        createStatistics(1, yesterday, 0, 5);
        createStatistics(1, twoDaysAgo, 0, 3);

        // 상품 2: 오늘 20개, 어제 15개, 그저께 10개 주문 (총 45개)
        createStatistics(2, today, 0, 20);
        createStatistics(2, yesterday, 0, 15);
        createStatistics(2, twoDaysAgo, 0, 10);

        // 상품 3: 오늘 8개, 어제 7개, 그저께 5개 주문 (총 20개)
        createStatistics(3, today, 0, 8);
        createStatistics(3, yesterday, 0, 7);
        createStatistics(3, twoDaysAgo, 0, 5);

        // 상품 5: 오늘 30개, 어제 25개, 그저께 20개 주문 (총 75개)
        createStatistics(5, today, 0, 30);
        createStatistics(5, yesterday, 0, 25);
        createStatistics(5, twoDaysAgo, 0, 20);

        // When: 상위 3개 인기 상품 조회
        List<Product> popularProducts = productService.getPopularProductsByOrderCount(3);

        // Then: 주문 수량 순으로 정렬되어야 함 (상품5 > 상품2 > 상품3)
        assertEquals(3, popularProducts.size());
        assertEquals(5, popularProducts.get(0).getProductId(), "1위는 상품 5 (75개)");
        assertEquals(2, popularProducts.get(1).getProductId(), "2위는 상품 2 (45개)");
        assertEquals(3, popularProducts.get(2).getProductId(), "3위는 상품 3 (20개)");
    }

    @Test
    @DisplayName("조회수와 주문 수량이 모두 있는 경우 각 기준으로 다른 순위")
    void different_ranking_by_view_and_order() {
        // Given: 조회수는 많지만 주문은 적은 상품 vs 조회수는 적지만 주문은 많은 상품
        LocalDate today = LocalDate.now();

        // 상품 1: 조회 1000회, 주문 5개
        createStatistics(1, today, 1000, 5);

        // 상품 2: 조회 100회, 주문 50개
        createStatistics(2, today, 100, 50);

        // When & Then: 조회수 기준 인기 상품
        List<Product> popularByViews = productService.getPopularProductsByViewCount(2);
        assertEquals(1, popularByViews.get(0).getProductId(), "조회수 기준 1위는 상품 1");
        assertEquals(2, popularByViews.get(1).getProductId(), "조회수 기준 2위는 상품 2");

        // When & Then: 주문 수량 기준 인기 상품
        List<Product> popularByOrders = productService.getPopularProductsByOrderCount(2);
        assertEquals(2, popularByOrders.get(0).getProductId(), "주문 기준 1위는 상품 2");
        assertEquals(1, popularByOrders.get(1).getProductId(), "주문 기준 2위는 상품 1");
    }

    @Test
    @DisplayName("통계가 없는 경우 빈 리스트 반환")
    void return_empty_list_when_no_statistics() {
        // When: 통계 데이터가 없는 상태에서 인기 상품 조회
        List<Product> popularByViews = productService.getPopularProductsByViewCount(5);
        List<Product> popularByOrders = productService.getPopularProductsByOrderCount(5);

        // Then: 빈 리스트 반환
        assertTrue(popularByViews.isEmpty());
        assertTrue(popularByOrders.isEmpty());
    }

    @Test
    @DisplayName("limit보다 적은 수의 상품만 있는 경우")
    void return_available_products_when_less_than_limit() {
        // Given: 2개 상품만 통계 생성
        LocalDate today = LocalDate.now();
        createStatistics(1, today, 100, 10);
        createStatistics(2, today, 50, 5);

        // When: 상위 5개 요청
        List<Product> popularByViews = productService.getPopularProductsByViewCount(5);

        // Then: 2개만 반환
        assertEquals(2, popularByViews.size());
    }

    @Test
    @DisplayName("3일 이전 데이터는 포함되지 않아야 함")
    void should_not_include_data_older_than_3_days() {
        // Given: 오늘, 3일 전, 4일 전 데이터
        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);
        LocalDate fourDaysAgo = today.minusDays(4);

        createStatistics(1, today, 100, 0);       // 포함됨
        createStatistics(2, threeDaysAgo, 200, 0); // 포함 안 됨 (3일 전)
        createStatistics(3, fourDaysAgo, 300, 0);  // 포함 안 됨 (4일 전)

        // When: 인기 상품 조회
        List<Product> popularProducts = productService.getPopularProductsByViewCount(10);

        // Then: 상품 1만 포함
        assertEquals(1, popularProducts.size());
        assertEquals(1, popularProducts.get(0).getProductId());
    }

    /**
     * 테스트용 통계 생성 헬퍼 메서드
     */
    private void createStatistics(int productId, LocalDate date, int viewCount, int orderCount) {
        int statsId = statisticsRepository.generateId();
        ProductStatistics stats = ProductStatistics.builder()
                .statisticsId(statsId)
                .productId(productId)
                .statisticsDate(date)
                .viewCount(viewCount)
                .orderCount(orderCount)
                .build();
        statisticsRepository.save(stats);
    }
}
