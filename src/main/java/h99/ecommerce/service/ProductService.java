package h99.ecommerce.service;

import h99.ecommerce.annotation.CustomTransactional;
import h99.ecommerce.annotation.DistributedLock;
import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.ProductStatistics;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.ProductRepository;
import h99.ecommerce.repository.ProductStatisticsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStatisticsRepository productStatisticsRepository;

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
    }

    /**
     * 인기 상품 조회 (최근 3일간 조회수 기준) 캐시 키: cache:product:popular:view:{limit} TTL: 1시간
     */
    @Cacheable(value = "popularProductsByView", key = "'cache:product:popular:view:' + #limit")
    public List<Product> getPopularProductsByViewCount(int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 오늘 포함 3일

        // 최근 3일간 통계 조회
        List<ProductStatistics> recentStats = productStatisticsRepository.findByDateBetween(startDate, endDate);

        // 상품별 조회수 합산
        Map<Long, Integer> productViewCounts = recentStats.stream()
                .collect(Collectors.groupingBy(
                        ProductStatistics::getProductId,
                        Collectors.summingInt(ProductStatistics::getViewCount)
                ));

        // 조회수 순으로 정렬하여 상위 N개 추출
        List<Long> topProductIds = productViewCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();

        // 상품 정보 조회
        return topProductIds.stream()
                .map(productRepository::findOne)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 인기 상품 조회 (최근 3일간 주문 수량 기준) 캐시 키: cache:product:popular:order:{limit} TTL: 1시간
     */
    @Cacheable(value = "popularProductsByOrder", key = "'cache:product:popular:order:' + #limit")
    public List<Product> getPopularProductsByOrderCount(int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 오늘 포함 3일

        // 최근 3일간 통계 조회
        List<ProductStatistics> recentStats = productStatisticsRepository.findByDateBetween(startDate, endDate);

        // 상품별 주문 수량 합산
        Map<Long, Integer> productOrderCounts = recentStats.stream()
                .collect(Collectors.groupingBy(
                        ProductStatistics::getProductId,
                        Collectors.summingInt(ProductStatistics::getOrderCount)
                ));

        // 주문 수량 순으로 정렬하여 상위 N개 추출
        List<Long> topProductIds = productOrderCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();

        // 상품 정보 조회
        return topProductIds.stream()
                .map(productRepository::findOne)
                .filter(Objects::nonNull)
                .toList();
    }

}
