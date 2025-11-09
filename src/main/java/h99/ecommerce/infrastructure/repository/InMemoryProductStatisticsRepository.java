package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.ProductStatistics;
import h99.ecommerce.repository.ProductStatisticsRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryProductStatisticsRepository implements ProductStatisticsRepository {

    private final Map<Integer, ProductStatistics> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public int generateId() {
        return idGenerator.getAndIncrement();
    }

    @Override
    public ProductStatistics save(ProductStatistics statistics) {
        if (statistics == null) {
            throw new IllegalArgumentException("ProductStatistics cannot be null");
        }
        store.put(statistics.getStatisticsId(), statistics);
        return statistics;
    }

    @Override
    public ProductStatistics findOne(int statisticsId) {
        return store.get(statisticsId);
    }

    @Override
    public Optional<ProductStatistics> findByProductIdAndDate(int productId, LocalDate date) {
        return store.values().stream()
                .filter(stats -> stats.getProductId() == productId)
                .filter(stats -> stats.getStatisticsDate().equals(date))
                .findFirst();
    }

    @Override
    public List<ProductStatistics> findByProductIdAndDateBetween(int productId, LocalDate startDate, LocalDate endDate) {
        return store.values().stream()
                .filter(stats -> stats.getProductId() == productId)
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
        idGenerator.set(1);
    }
}
