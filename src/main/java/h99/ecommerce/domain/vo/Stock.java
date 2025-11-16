package h99.ecommerce.domain.vo;

import h99.ecommerce.exception.NotEnoughStockException;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.Getter;

@Embeddable
@Getter
public class Stock {

    private int quantity;

    public Stock(int quantity) {
        if (quantity < 0) {
            throw new NotEnoughStockException("재고 수량이 부족합니다.");
        }
        this.quantity = quantity;
    }

    protected Stock() {
        // JPA용 기본 생성자
    }

    public Stock add(Stock other) {
        return new Stock(this.quantity + other.quantity);
    }

    public Stock reduce(Stock other) {
        int restQuantity = this.quantity - other.quantity;
        if (restQuantity < 0) {
            throw new NotEnoughStockException("차감 후 수량은 0 이상이어야 합니다.");
        }
        return new Stock(restQuantity);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Stock stock = (Stock) obj;
        return quantity == stock.quantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(quantity);
    }
}
