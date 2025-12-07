package h99.ecommerce.domain.product;

import java.util.List;
import java.util.Map;

public interface PopularProductRepository {

    void save(String priod, Long productId, double score);
    void saveAll(String priod, Map<Long, Double> productScores);
    List<Long> getTopProducts(String priod, int limit);
    Long getLank(String period, Long productId);
    void setExpiration(String period, long seconds);
    void incrementScore(String period, Long productId, double delta);
    Double getScore(String period, Long productId);
    boolean exists(String period);
}
