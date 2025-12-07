package h99.ecommerce.infrastructure.repository.redis;

import h99.ecommerce.domain.product.PopularProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisPopularProductRepository implements PopularProductRepository {

    private final RedissonClient redissonClient;
    private static final String KEY_PREFIX = "popular_products:";

    /**
     * 단일 상품 저장/업데이트
     */
    @Override
    public void save(String priod, Long productId, double score) {
        String key = KEY_PREFIX + priod;
        RScoredSortedSet<Object> sortedSet = redissonClient.getScoredSortedSet(key);
        sortedSet.add(score, productId);
        log.debug("Saved product {} with score {} to {}", productId, score, key);
    }

    /**
     * 여러 상품 일괄 저장(스케줄러)
     * 기존 데이터를 유지하면서 업데이트 (incremental update)
     * Scheduler에서 전체 갱신이 필요한 경우 clearAll() 먼저 호출
     */
    @Override
    public void saveAll(String priod, Map<Long, Double> productScores) {
        if (productScores.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + priod;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(key);

        // addAll은 기존 member의 score를 업데이트하거나 새로 추가
        sortedSet.addAll(productScores);
        log.debug("Saved products {} to {}", productScores.size(), key);
    }

    /**
     * 특정 period의 모든 데이터 삭제
     */
    public void clearAll(String period) {
        String key = KEY_PREFIX + period;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(key);
        sortedSet.delete();
        log.info("Cleared all data for period: {}", period);
    }

    /**
     * 점수 높은 순 조회
     */
    @Override
    public List<Long> getTopProducts(String priod, int limit) {
        String key = KEY_PREFIX + priod;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(key);

        return new ArrayList<>(sortedSet.valueRangeReversed(0, limit - 1));
    }

    /**
     * 특정 상품의 순위 조회
     */
    @Override
    public Long getLank(String period, Long productId) {
        String key = KEY_PREFIX + period;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(key);

        Integer rank = sortedSet.revRank(productId);

        if (rank == null) {
            return null;
        }

        return (long) (rank + 1);
    }

    /**
     * TTL 설정
     */
    @Override
    public void setExpiration(String period, long seconds) {
        String key = KEY_PREFIX + period;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(key);
        sortedSet.expire(seconds, TimeUnit.SECONDS);
        log.info("Set expiration for {} to {} seconds", key, seconds);
    }

    /**
     * 실시간 점수 증가 (ZINCRBY)
     */
    @Override
    public void incrementScore(String period, Long productId, double delta) {
        String key = KEY_PREFIX + period;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(key);

        // addScore는 기존 점수에 delta를 더함 (ZINCRBY)
        sortedSet.addScore(productId, delta);

        log.debug("Incremented score of product {} by {} in {}", productId, delta, key);
    }

    /**
     * 특정 상품 점수 조회
     */
    @Override
    public Double getScore(String period, Long productId) {
        String Key = KEY_PREFIX + period;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(Key);
        return sortedSet.getScore(productId);
    }

    /**
     * 키 존재 여부 확인
     */
    @Override
    public boolean exists(String period) {
        String Key = KEY_PREFIX + period;
        RScoredSortedSet<Long> sortedSet = redissonClient.getScoredSortedSet(Key);
        return sortedSet.isExists();
    }
}
