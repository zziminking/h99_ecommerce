package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.ProductStatistics;
import h99.ecommerce.repository.ProductStatisticsRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemoryProductStatisticsRepository implements ProductStatisticsRepository {

    private final Map<Long, ProductStatistics> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public ProductStatistics save(ProductStatistics statistics) {
        if (statistics == null) {
            throw new IllegalArgumentException("ProductStatistics cannot be null");
        }

        if (statistics.getStatisticsId() == null) {
            // Auto-generate ID for new statistics
            Long newId = (long) idGenerator.getAndIncrement();
            ProductStatistics newStatistics = ProductStatistics.builder()
                    .statisticsId(newId)
                    .productId(statistics.getProductId())
                    .statisticsDate(statistics.getStatisticsDate())
                    .viewCount(statistics.getViewCount())
                    .orderCount(statistics.getOrderCount())
                    .build();
            store.put(newId, newStatistics);
            return newStatistics;
        }

        store.put(statistics.getStatisticsId(), statistics);
        return statistics;
    }

    @Override
    public ProductStatistics findOne(Long statisticsId) {
        return store.get(statisticsId);
    }

    @Override
    public Optional<ProductStatistics> findByProductIdAndDate(Long productId, LocalDate date) {
        return store.values().stream()
                .filter(stats -> stats.getProductId().equals(productId))
                .filter(stats -> stats.getStatisticsDate().equals(date))
                .findFirst();
    }

    @Override
    public List<ProductStatistics> findByProductIdAndDateBetween(Long productId, LocalDate startDate, LocalDate endDate) {
        return store.values().stream()
                .filter(stats -> stats.getProductId().equals(productId))
                .filter(stats -> !stats.getStatisticsDate().isBefore(startDate))
                .filter(stats -> !stats.getStatisticsDate().isAfter(endDate))
                .toList();
    }

    @Override
    public List<ProductStatistics> findByDateBetween(LocalDate startDate, LocalDate endDate) {
        return store.values().stream()
                .filter(stats -> !stats.getStatisticsDate().isBefore(startDate))
                .filter(stats -> !stats.getStatisticsDate().isAfter(endDate))
                .toList();
    }

    @Override
    public List<ProductStatistics> findAll() {
        return List.copyOf(store.values());
    }

    public void clear() {
        store.clear();
    }
}
