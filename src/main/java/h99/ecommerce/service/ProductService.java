package h99.ecommerce.service;

import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.ProductStatistics;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.ProductRepository;
import h99.ecommerce.repository.ProductStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStatisticsRepository productStatisticsRepository;

    /**
     * 상품 단건 조회
     */
    public Product getProduct(int productId) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        // 조회수 증가
        product.increaseViewCount();
        productRepository.save(product);

        // 일별 통계 업데이트
        updateDailyViewStatistics(productId);

        return product;
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
    public boolean checkStockAvailable(int productId) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product.hasStock();
    }

    /**
     * 재고 충분 여부 확인
     */
    public boolean checkStockEnough(int productId, int quantity) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product.hasEnoughStock(quantity);
    }

    /**
     * 재고 차감
     */
    public void deductStock(int productId, int quantity) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        if (!product.hasStock()) {
            throw new NotEnoughStockException("품절된 상품입니다.");
        }

        if (!product.hasEnoughStock(quantity)) {
            throw new NotEnoughStockException("재고가 부족합니다. 현재 재고: " + product.getStock().getQuantity());
        }

        product.deductStock(quantity);
        productRepository.save(product);
    }

    /**
     * 재고 복구
     */
    public void restoreStock(int productId, int quantity) {
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
    private void updateDailyViewStatistics(int productId) {
        LocalDate today = LocalDate.now();
        Optional<ProductStatistics> existingStats = productStatisticsRepository.findByProductIdAndDate(productId, today);

        if (existingStats.isPresent()) {
            ProductStatistics stats = existingStats.get();
            stats.increaseViewCount();
            productStatisticsRepository.save(stats);
        } else {
            int statsId = productStatisticsRepository.generateId();
            ProductStatistics newStats = ProductStatistics.builder()
                    .statisticsId(statsId)
                    .productId(productId)
                    .statisticsDate(today)
                    .viewCount(1)
                    .orderCount(0)
                    .build();
            productStatisticsRepository.save(newStats);
        }
    }

    /**
     * 주문 수량 통계 업데이트 (주문 서비스에서 호출)
     */
    public void updateOrderStatistics(int productId, int quantity) {
        LocalDate today = LocalDate.now();
        Optional<ProductStatistics> existingStats = productStatisticsRepository.findByProductIdAndDate(productId, today);

        if (existingStats.isPresent()) {
            ProductStatistics stats = existingStats.get();
            stats.increaseOrderCount(quantity);
            productStatisticsRepository.save(stats);
        } else {
            int statsId = productStatisticsRepository.generateId();
            ProductStatistics newStats = ProductStatistics.builder()
                    .statisticsId(statsId)
                    .productId(productId)
                    .statisticsDate(today)
                    .viewCount(0)
                    .orderCount(quantity)
                    .build();
            productStatisticsRepository.save(newStats);
        }
    }

    /**
     * 인기 상품 조회 (최근 3일간 조회수 기준)
     */
    public List<Product> getPopularProductsByViewCount(int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 오늘 포함 3일

        // 최근 3일간 통계 조회
        List<ProductStatistics> recentStats = productStatisticsRepository.findByDateBetween(startDate, endDate);

        // 상품별 조회수 합산
        Map<Integer, Integer> productViewCounts = recentStats.stream()
                .collect(Collectors.groupingBy(
                        ProductStatistics::getProductId,
                        Collectors.summingInt(ProductStatistics::getViewCount)
                ));

        // 조회수 순으로 정렬하여 상위 N개 추출
        List<Integer> topProductIds = productViewCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
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
     * 인기 상품 조회 (최근 3일간 주문 수량 기준)
     */
    public List<Product> getPopularProductsByOrderCount(int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 오늘 포함 3일

        // 최근 3일간 통계 조회
        List<ProductStatistics> recentStats = productStatisticsRepository.findByDateBetween(startDate, endDate);

        // 상품별 주문 수량 합산
        Map<Integer, Integer> productOrderCounts = recentStats.stream()
                .collect(Collectors.groupingBy(
                        ProductStatistics::getProductId,
                        Collectors.summingInt(ProductStatistics::getOrderCount)
                ));

        // 주문 수량 순으로 정렬하여 상위 N개 추출
        List<Integer> topProductIds = productOrderCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
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
