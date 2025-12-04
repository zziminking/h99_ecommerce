package h99.ecommerce.scheduler;

import h99.ecommerce.domain.product.PopularProductRepository;
import h99.ecommerce.domain.product.ProductStatistics;
import h99.ecommerce.domain.product.ProductStatisticsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularProductScheduler {

    private final ProductStatisticsRepository productStatisticsRepository;
    private final PopularProductRepository popularProductRepository;

    /**
     * 매일 자정에 인기 상품 데이터 재계산
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void updatePopularProducts() {
        log.info("=== Starting popular products update ===");

        try {
            // 3일간 인기 상품 업데이트
            updatePopularProductsByPeriod("3days", 3);

            // 7일간 인기 상품 업데이트
            updatePopularProductsByPeriod("7days", 7);

            log.info("=== Popular products update completed ===");
        } catch (Exception e) {
            log.error("Failed to update popular products", e);
        }
    }

    /**
     * 애플리케이션 시작 5초 후 초기 데이터 로드
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void initializePopularProducts() {
        log.info("=== Initializing popular products on startup ===");

        try {
            // 키가 없으면 초기화
            if (!popularProductRepository.exists("3days")) {
                log.info("3days key not found, initializing...");
                updatePopularProductsByPeriod("3days", 3);
            }

            if (!popularProductRepository.exists("7days")) {
                log.info("7days key not found, initializing...");
                updatePopularProductsByPeriod("7days", 7);
            }

            log.info("=== Popular products initialization completed ===");
        } catch (Exception e) {
            log.error("Failed to initialize popular products", e);
        }

    }

    /**
     * 특정 기간의 인기 상품 업데이트
     */
    private void updatePopularProductsByPeriod(String period, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<ProductStatistics> statistics = productStatisticsRepository.findByDateBetween(startDate, endDate);

        if (statistics.isEmpty()) {
            return;
        }

        Map<Long, Double> productScores = aggregateScores(statistics);

        // 기존 데이터 전체 삭제 후 새로 저장 (전체 갱신)
        if (popularProductRepository instanceof h99.ecommerce.infrastructure.repository.redis.RedisPopularProductRepository) {
            ((h99.ecommerce.infrastructure.repository.redis.RedisPopularProductRepository) popularProductRepository)
                .clearAll(period);
        }

        popularProductRepository.saveAll(period, productScores);

        long ttlSeconds = TimeUnit.DAYS.toSeconds(days + 1);
        popularProductRepository.setExpiration(period, ttlSeconds);
    }

    /**
     * ProductStatistics 점수 집계
     * viewCount + orderCount * 5 (주문 1건당 5점)
     */
    private Map<Long, Double> aggregateScores(List<ProductStatistics> statistics) {
        return statistics.stream()
                .collect(Collectors.groupingBy(ProductStatistics::getProductId,
                        Collectors.summingDouble(it -> it.getViewCount() + (it.getOrderCount() * 5.0))));
    }

}
