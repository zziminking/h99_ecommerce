package h99.ecommerce.domain;

import h99.ecommerce.domain.vo.Stock;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class Product {

    private int productId;
    private String name;
    private String description;
    private BigDecimal price;
    private Stock stock;
    private int totalViewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product(int productId, String name, String description, BigDecimal price, Stock stock, int totalViewCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        validatePrice(price);
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock == null ? new Stock(0) : stock;
        this.totalViewCount = totalViewCount;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    private void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("가격은 필수입니다.");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    /**
     * 재고 차감
     */
    public void deductStock(int quantity) {
        Stock deductAmount = new Stock(quantity);
        this.stock = this.stock.reduce(deductAmount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 복구
     */
    public void restoreStock(int quantity) {
        Stock restoreAmount = new Stock(quantity);
        this.stock = this.stock.add(restoreAmount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 확인
     */
    public boolean hasEnoughStock(int quantity) {
        return this.stock.getQuantity() >= quantity;
    }

    /**
     * 재고 존재 확인
     */
    public boolean hasStock() {
        return this.stock.getQuantity() > 0;
    }

    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.totalViewCount++;
        this.updatedAt = LocalDateTime.now();
    }

}
