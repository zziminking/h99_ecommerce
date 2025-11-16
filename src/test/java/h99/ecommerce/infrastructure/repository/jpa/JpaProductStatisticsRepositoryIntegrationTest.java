package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;

import h99.ecommerce.domain.ProductStatistics;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaProductStatisticsRepository 통합 테스트")
class JpaProductStatisticsRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaProductStatisticsRepository productStatisticsRepository;

    @Test
    @DisplayName("상품 통계 저장")
    void save_product_statistics() {
        // given
        ProductStatistics statistics = ProductStatistics.builder()
                .productId(1L)
                .statisticsDate(LocalDate.now())
                .viewCount(100)
                .orderCount(10)
                .build();

        // when
        ProductStatistics saved = productStatisticsRepository.save(statistics);
        flushAndClear();

        // then
        assertThat(statistics.getStatisticsId()).isEqualTo(saved.getStatisticsId());
    }

    @Test
    @DisplayName("상품 통계 조회")
    void findOne_by_id() {
        // given
        ProductStatistics statistics = ProductStatistics.builder()
                .productId(2L)
                .statisticsDate(LocalDate.now())
                .viewCount(200)
                .orderCount(20)
                .build();
        ProductStatistics saved = productStatisticsRepository.save(statistics);
        flushAndClear();

        // when
        ProductStatistics found = productStatisticsRepository.findOne(saved.getStatisticsId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getProductId()).isEqualTo(2L);
        assertThat(found.getViewCount()).isEqualTo(200);
        assertThat(found.getOrderCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("상품 ID와 날짜로 통계 조회")
    void find_by_product_id_and_date() {
        // given
        LocalDate today = LocalDate.now();
        ProductStatistics statistics = ProductStatistics.builder()
                .productId(3L)
                .statisticsDate(today)
                .viewCount(150)
                .orderCount(15)
                .build();
        productStatisticsRepository.save(statistics);
        flushAndClear();

        // when
        Optional<ProductStatistics> found = productStatisticsRepository.findByProductIdAndDate(3L, today);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getViewCount()).isEqualTo(150);
        assertThat(found.get().getOrderCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("상품 ID와 날짜 범위로 통계 조회")
    void find_by_product_id_and_date_between() {
        // given
        Long productId = 4L;
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        ProductStatistics stats1 = ProductStatistics.builder()
                .productId(productId)
                .statisticsDate(twoDaysAgo)
                .viewCount(100)
                .orderCount(10)
                .build();
        ProductStatistics stats2 = ProductStatistics.builder()
                .productId(productId)
                .statisticsDate(yesterday)
                .viewCount(200)
                .orderCount(20)
                .build();
        ProductStatistics stats3 = ProductStatistics.builder()
                .productId(productId)
                .statisticsDate(today)
                .viewCount(300)
                .orderCount(30)
                .build();

        productStatisticsRepository.save(stats1);
        productStatisticsRepository.save(stats2);
        productStatisticsRepository.save(stats3);
        flushAndClear();

        // when
        List<ProductStatistics> found = productStatisticsRepository.findByProductIdAndDateBetween(
                productId, twoDaysAgo, today);

        // then
        assertThat(found).hasSize(3);
    }

    @Test
    @DisplayName("날짜 범위로 통계 조회")
    void find_by_date_between() {
        // given
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        ProductStatistics stats1 = ProductStatistics.builder()
                .productId(5L)
                .statisticsDate(yesterday)
                .viewCount(100)
                .orderCount(10)
                .build();
        ProductStatistics stats2 = ProductStatistics.builder()
                .productId(6L)
                .statisticsDate(today)
                .viewCount(200)
                .orderCount(20)
                .build();

        productStatisticsRepository.save(stats1);
        productStatisticsRepository.save(stats2);
        flushAndClear();

        // when
        List<ProductStatistics> found = productStatisticsRepository.findByDateBetween(yesterday, today);

        // then
        assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("모든 통계 조회")
    void find_all_statistics() {
        // given
        ProductStatistics stats1 = ProductStatistics.builder()
                .productId(7L)
                .statisticsDate(LocalDate.now())
                .viewCount(50)
                .orderCount(5)
                .build();
        ProductStatistics stats2 = ProductStatistics.builder()
                .productId(8L)
                .statisticsDate(LocalDate.now())
                .viewCount(100)
                .orderCount(10)
                .build();

        productStatisticsRepository.save(stats1);
        productStatisticsRepository.save(stats2);
        flushAndClear();

        // when
        List<ProductStatistics> all = productStatisticsRepository.findAll();

        // then
        assertThat(all.size()).isGreaterThanOrEqualTo(2);
    }
}
