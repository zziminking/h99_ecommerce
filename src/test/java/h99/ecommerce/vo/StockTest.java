package h99.ecommerce.vo;

import static org.junit.jupiter.api.Assertions.*;

import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class StockTest {

    @Test
    @DisplayName("음수 재고 생성 시 에러 발생")
    void create_minus_stock_fail() {
        assertThrows(NotEnoughStockException.class, () -> new Stock(-1));
    }

    @Test
    @DisplayName("재고 차감 시 잔여 재고 없으면 에러")
    void reduce_not_enough_stock_fail() {
        Stock stock = new Stock(10);
        assertThrows(NotEnoughStockException.class, () -> stock.reduce(new Stock(11)));
    }

    @Test
    @DisplayName("재고 증가 - 성공")
    void add_stock_success() {
        Stock stock = new Stock(10);
        Stock other = new Stock(5);

        Stock result = stock.add(other);

        assertEquals(15, result.quantity);
    }

    @Test
    @DisplayName("재고 차감 - 성공")
    void reduce_stock_success() {
        Stock stock = new Stock(10);
        Stock other = new Stock(5);

        Stock result = stock.reduce(other);

        assertEquals(5, result.quantity);
    }

    @Test
    @DisplayName("재고 복구 - 성공")
    void rollback_stock_success() {
        Stock stock = new Stock(10);
        Stock reducedStock = stock.reduce(new Stock(5));
        Stock rolledBackStock = reducedStock.rollback();

        assertEquals(10, rolledBackStock.quantity);
    }

}