package h99.ecommerce.repository;

import h99.ecommerce.domain.ProductStatistics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductStatisticsRepository {

    int generateId();

    ProductStatistics save(ProductStatistics statistics);

    ProductStatistics findOne(int statisticsId);

    /**
     * 특정 날짜의 상품 통계 조회
     */
    Optional<ProductStatistics> findByProductIdAndDate(int productId, LocalDate date);

    /**
     * 특정 기간 내의 상품 통계 조회
     */
    List<ProductStatistics> findByProductIdAndDateBetween(int productId, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 기간 내의 모든 통계 조회
     */
    List<ProductStatistics> findByDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * 전체 통계 조회
     */
    List<ProductStatistics> findAll();
}
