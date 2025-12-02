package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.product.ProductStatistics;
import h99.ecommerce.domain.product.ProductStatisticsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaProductStatisticsRepository implements ProductStatisticsRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public ProductStatistics save(ProductStatistics statistics) {
        if (statistics.getStatisticsId() == null) {
            em.persist(statistics);
            return statistics;
        } else {
            return em.merge(statistics);
        }
    }

    @Override
    public ProductStatistics findOne(Long statisticsId) {
        return em.find(ProductStatistics.class, statisticsId);
    }

    @Override
    public Optional<ProductStatistics> findByProductIdAndDate(Long productId, LocalDate date) {
        List<ProductStatistics> resultList = em.createQuery("SELECT ps FROM ProductStatistics ps"
                        + " WHERE ps.productId = :productId AND ps.statisticsDate = :date", ProductStatistics.class)
                .setParameter("productId", productId)
                .setParameter("date", date)
                .getResultList();

        return resultList.isEmpty() ? Optional.empty() : Optional.of(resultList.get(0));
    }

    @Override
    public List<ProductStatistics> findByProductIdAndDateBetween(Long productId, LocalDate startDate,
                                                                 LocalDate endDate) {
        return em.createQuery(
                        "SELECT ps FROM ProductStatistics ps " +
                                "WHERE ps.productId = :productId " +
                                "AND ps.statisticsDate BETWEEN :startDate " +
                                "AND ps.statisticsDate AND :endDate " +
                                "ORDER BY ps.statisticsDate DESC",
                        ProductStatistics.class)
                .setParameter("productId", productId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();
    }

    @Override
    public List<ProductStatistics> findByDateBetween(LocalDate startDate, LocalDate endDate) {
        return em.createQuery(
                        "SELECT ps FROM ProductStatistics ps " +
                                "WHERE ps.statisticsDate BETWEEN :startDate AND :endDate " +
                                "ORDER BY ps.statisticsDate DESC",
                        ProductStatistics.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

    }

    @Override
    public List<ProductStatistics> findAll() {
        return em.createQuery(
                        "SELECT ps FROM ProductStatistics ps ORDER BY ps.statisticsDate DESC",
                        ProductStatistics.class)
                .getResultList();

    }
}
