package h99.ecommerce.domain.vo;

import h99.ecommerce.exception.NotEnoughStockException;
import java.util.Objects;
import lombok.Getter;

@Getter
public class Stock {

    public final int quantity;
    private final Stock previousStock;

    public Stock(int quantity) {
        if (quantity < 0) {
            throw new NotEnoughStockException("재고 수량이 부족합니다.");
        }
        this.quantity = quantity;
        this.previousStock = null;
    }

    private Stock(int quantity, Stock previousStock) {
        if (quantity < 0) {
            throw new NotEnoughStockException("재고 수량이 부족합니다.");
        }
        this.quantity = quantity;
        this.previousStock = previousStock;
    }

    public Stock add(Stock other) {
        return new Stock(this.quantity + other.quantity, this);
    }

    public Stock reduce(Stock other) {
        int restQuantity = this.quantity - other.quantity;
        if (restQuantity < 0) {
            throw new NotEnoughStockException("차감 후 수량은 0 이상이어야 합니다.");
        }
        return new Stock(restQuantity, this);
    }

    public Stock rollback() {
        if (this.previousStock == null) {
            return this;
        }
        return this.previousStock;
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
