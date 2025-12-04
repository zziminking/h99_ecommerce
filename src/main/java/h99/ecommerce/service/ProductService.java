package h99.ecommerce.service;

import com.esotericsoftware.minlog.Log;
import h99.ecommerce.annotation.CustomTransactional;
import h99.ecommerce.annotation.DistributedLock;
import h99.ecommerce.domain.product.PopularProductRepository;
import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductStatistics;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.domain.product.ProductRepository;
import h99.ecommerce.domain.product.ProductStatisticsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStatisticsRepository productStatisticsRepository;
    private final PopularProductRepository popularProductRepository;

    /**
     * 상품 단건 조회 (캐시 적용) 캐시 키: cache:product:detail:id:{productId} TTL: 30분
     */
    @Cacheable(value = "product", key = "'cache:product:detail:id:' + #productId")
    public Product getProduct(Long productId) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product;
    }

    /**
     * 상품 조회 with 조회수 증가 조회수 증가가 필요한 경우 이 메서드 사용
     */
    public Product getProductWithViewCount(Long productId) {
        Product product = getProduct(productId);
        increaseViewCountAsync(productId);
        return product;
    }

    /**
     * 조회수 증가 (캐시 없이 매번 실행)
     */
    @Async
    protected void increaseViewCountAsync(Long productId) {
        Product product = productRepository.findOne(productId);
        if (product != null) {
            product.increaseViewCount();
            productRepository.save(product);
            updateDailyViewStatistics(productId);

            try {
                popularProductRepository.incrementScore("3days", productId, 1.0);
                popularProductRepository.incrementScore("7days", productId, 1.0);
            } catch (Exception e) {
                log.error("레디스 score 증가 실패, productId: {}", productId, e);
            }
        }
    }

    /**
     * 상품 전체 조회
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * 재고 존재 여부 확인
     */
    public boolean checkStockAvailable(Long productId) {
        Product product = getProduct(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product.hasStock();
    }

    /**
     * 재고 충분 여부 확인
     */
    public boolean checkStockEnough(Long productId, int quantity) {
        Product product = getProduct(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product.hasEnoughStock(quantity);
    }

    /**
     * 재고 차감 재고 변경 시 상품 캐시 무효화
     */
    @DistributedLock(key = "product:stock:#{#productId}")
    @CustomTransactional
    @CacheEvict(value = "product", key = "'cache:product:detail:id:' + #productId")
    public void deductStock(Long productId, int quantity) {
        Product product = productRepository.findOne(productId);

        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        if (!product.hasStock()) {
            throw new NotEnoughStockException("품절된 상품입니다.");
        }

        if (!product.hasEnoughStock(quantity)) {
            throw new NotEnoughStockException(
                    "재고가 부족합니다. 요청수량: " + quantity + ", 현재수량: " + product.getStock().getQuantity());
        }

        product.deductStock(quantity);
        productRepository.save(product);
    }

    /**
     * 재고 복구 재고 변경 시 상품 캐시 무효화
     */
    @CacheEvict(value = "product", key = "'cache:product:detail:id:' + #productId")
    public void restoreStock(Long productId, int quantity) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        product.restoreStock(quantity);
        productRepository.save(product);
    }

    /**
     * 일별 조회수 통계 업데이트
     */
    private void updateDailyViewStatistics(Long productId) {
        LocalDate today = LocalDate.now();
        Optional<ProductStatistics> existingStats = productStatisticsRepository.findByProductIdAndDate(productId,
                today);

        if (existingStats.isPresent()) {
            ProductStatistics stats = existingStats.get();
            stats.increaseViewCount();
            productStatisticsRepository.save(stats);
        } else {
            ProductStatistics newStats = ProductStatistics.builder()
                    .productId(productId)
                    .statisticsDate(today)
                    .viewCount(1)
                    .orderCount(0)
                    .build();
            productStatisticsRepository.save(newStats);
        }
    }

    /**
     * 주문 수량 통계 업데이트 (주문 서비스에서 호출) 통계 업데이트 시 인기 상품 캐시 삭제
     */
    @CacheEvict(value = {"popularProductsByView", "popularProductsByOrder"}, allEntries = true)
    public void updateOrderStatistics(Long productId, int quantity) {
        LocalDate today = LocalDate.now();
        Optional<ProductStatistics> existingStats = productStatisticsRepository.findByProductIdAndDate(productId,
                today);

        if (existingStats.isPresent()) {
            ProductStatistics stats = existingStats.get();
            stats.increaseOrderCount(quantity);
            productStatisticsRepository.save(stats);
        } else {
            ProductStatistics newStats = ProductStatistics.builder()
                    .productId(productId)
                    .statisticsDate(today)
                    .viewCount(0)
                    .orderCount(quantity)
                    .build();
            productStatisticsRepository.save(newStats);
        }

        // Redis 인기 상품 score 증가 (주문 1건당 +5점)
        try {
            double scoreIncrement = quantity * 5.0;
            popularProductRepository.incrementScore("3days", productId, scoreIncrement);
            popularProductRepository.incrementScore("7days", productId, scoreIncrement);
        } catch (Exception e) {
            log.error("레디스 score 증가 실패, productId: {}", productId, e);
        }
    }

    /**
     * 인기 상품 조회(redis)
     */
    public List<Product> getPopularProducts(String period, int limit) {
        try {
            List<Long> productIds = popularProductRepository.getTopProducts(period, limit);

            if (productIds.isEmpty()) {
                log.warn("해당 기간 인기상품 목록이 없습니다. 기간: {}", period);
                return List.of();
            }

            return productIds.stream()
                    .map(this::getProduct)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("인기상품 조회에 실패하였습니다.");
            return List.of();
        }
    }
}
