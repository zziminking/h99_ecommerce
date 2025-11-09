package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 상품 통계 도메인
 * 일별 조회수와 주문 수량을 추적
 */
@Getter
@Builder
public class ProductStatistics {

    private int statisticsId;
    private int productId;
    private LocalDate statisticsDate;  // 통계 날짜
    private int viewCount;             // 일별 조회수
    private int orderCount;            // 일별 주문 수량
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProductStatistics(int statisticsId, int productId, LocalDate statisticsDate,
                             int viewCount, int orderCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.statisticsId = statisticsId;
        this.productId = productId;
        this.statisticsDate = statisticsDate == null ? LocalDate.now() : statisticsDate;
        this.viewCount = viewCount;
        this.orderCount = orderCount;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 수량 증가
     */
    public void increaseOrderCount(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        this.orderCount += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 총 점수 계산 (조회수 + 주문 수량 * 10)
     * 주문이 조회보다 더 중요하므로 가중치 적용
     */
    public int calculateScore() {
        return this.viewCount + (this.orderCount * 10);
    }
}
