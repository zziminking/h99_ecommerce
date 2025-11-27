package h99.ecommerce.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import h99.ecommerce.domain.vo.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@lombok.EqualsAndHashCode
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price")
    private BigDecimal price;

    @Embedded
    private Stock stock;

    @Column(name = "total_view_count")
    private int totalViewCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Builder를 사용할 때 유효성 검사를 위한 내부 클래스
    public static class ProductBuilder {
        public Product build() {
            if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
            }
            if (stock == null) {
                stock = new Stock(0);
            }
            if (createdAt == null) {
                createdAt = LocalDateTime.now();
            }
            if (updatedAt == null) {
                updatedAt = LocalDateTime.now();
            }
            return new Product(productId, name, description, price, stock, totalViewCount, createdAt, updatedAt);
        }
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
