package h99.ecommerce.domain;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.order.OrderItem;
import h99.ecommerce.domain.order.OrderStatus;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.cartitem.CartItem;
import h99.ecommerce.domain.point.Point;

import static org.junit.jupiter.api.Assertions.*;

import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ProductTest {

    private Product testProduct;
    private Product stockEmptyProduct;
    private Product lowStockProduct;

    @BeforeEach
    void setUp() {
        // 일반 상품 (재고 100개)
        testProduct = new Product(
                1L,
                "테스트 상품",
                "테스트 상품 설명",
                new BigDecimal("10000"),
                new Stock(100),
                0,
                null,
                null
        );

        // 재고 없는 상품
        stockEmptyProduct = new Product(
                2L,
                "품절 상품",
                "품절된 상품",
                new BigDecimal("20000"),
                new Stock(0),
                0,
                null,
                null
        );

        // 재고 적은 상품 (10개)
        lowStockProduct = new Product(
                3L,
                "재고 적은 상품",
                "재고가 적은 상품",
                new BigDecimal("5000"),
                new Stock(10),
                0,
                null,
                null
        );
    }

    @Test
    @DisplayName("상품 생성 - 성공")
    void create_product_success() {
        // given
        Long productId = 1L;
        String name = "테스트 상품";
        String description = "테스트 상품 설명";
        BigDecimal price = new BigDecimal("10000");
        Stock stock = new Stock(100);
        int totalViewCount = 0;
        LocalDateTime now = LocalDateTime.now();

        // when
        Product product = new Product(productId, name, description, price, stock, totalViewCount, now, now);

        // then
        assertEquals(productId, product.getProductId());
        assertEquals(name, product.getName());
        assertEquals(description, product.getDescription());
        assertEquals(price, product.getPrice());
        assertEquals(100, product.getStock().getQuantity());
        assertEquals(totalViewCount, product.getTotalViewCount());
    }

    @Test
    @DisplayName("상품 생성 시 Stock이 null이면 기본값 0으로 설정")
    void create_product_with_null_stock_success() {
        // given
        Long productId = 1L;
        String name = "테스트 상품";
        String description = "설명";
        BigDecimal price = new BigDecimal("10000");
        Stock stock = null;

        // when
        Product product = new Product(productId, name, description, price, stock, 0, null, null);

        // then
        assertEquals(0, product.getStock().getQuantity());
        assertNotNull(product.getCreatedAt());
        assertNotNull(product.getUpdatedAt());
    }

    @Test
    @DisplayName("상품 생성 시 가격이 null이면 예외 발생")
    void create_product_with_null_price_fail() {
        // given
        String name = "테스트 상품";
        String description = "설명";
        BigDecimal price = null;
        Stock stock = new Stock(100);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Product(1L, name, description, price, stock, 0, null, null)
        );
        assertEquals("가격은 필수입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("상품 생성 시 가격이 음수면 예외 발생")
    void create_product_with_negative_price_fail() {
        // given
        String name = "테스트 상품";
        String description = "설명";
        BigDecimal price = new BigDecimal("-1000");
        Stock stock = new Stock(100);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Product(1L, name, description, price, stock, 0, null, null)
        );
        assertEquals("가격은 0 이상이어야 합니다.", exception.getMessage());
    }

    @Test
    @DisplayName("재고 차감 - 성공")
    void deduct_stock_success() {
        // given
        int deductQuantity = 30;

        // when
        testProduct.deductStock(deductQuantity);

        // then
        assertEquals(70, testProduct.getStock().getQuantity());
    }

    @Test
    @DisplayName("재고 차감 시 재고가 부족하면 예외 발생")
    void deduct_stock_with_not_enough_stock_fail() {
        // given
        int deductQuantity = 20;

        // when & then
        assertThrows(NotEnoughStockException.class, () -> lowStockProduct.deductStock(deductQuantity));
    }

    @Test
    @DisplayName("재고 복구 - 성공")
    void restore_stock_success() {
        // given
        testProduct.deductStock(30);
        int restoreQuantity = 20;

        // when
        testProduct.restoreStock(restoreQuantity);

        // then
        assertEquals(90, testProduct.getStock().getQuantity());
    }

    @Test
    @DisplayName("재고 확인 - 충분한 재고")
    void has_enough_stock_true() {
        // when
        boolean result = testProduct.hasEnoughStock(50);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("재고 확인 - 부족한 재고")
    void has_enough_stock_false() {
        // when
        boolean result = lowStockProduct.hasEnoughStock(50);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("재고 존재 확인 - 재고 있음")
    void has_stock_true() {
        // when
        boolean result = lowStockProduct.hasStock();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("재고 존재 확인 - 재고 없음")
    void has_stock_false() {
        // when
        boolean result = stockEmptyProduct.hasStock();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("조회수 증가 - 성공")
    void increase_view_count_success() {
        // when
        testProduct.increaseViewCount();
        testProduct.increaseViewCount();
        testProduct.increaseViewCount();

        // then
        assertEquals(3, testProduct.getTotalViewCount());
    }

    @Test
    @DisplayName("Builder 패턴으로 생성 - 성공")
    void create_with_builder_success() {
        // given & when
        Product product = Product.builder()
                .productId(1L)
                .name("테스트 상품")
                .description("테스트 설명")
                .price(new BigDecimal("10000"))
                .stock(new Stock(100))
                .totalViewCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // then
        assertEquals(1, product.getProductId());
        assertEquals("테스트 상품", product.getName());
        assertEquals("테스트 설명", product.getDescription());
        assertEquals(new BigDecimal("10000"), product.getPrice());
        assertEquals(100, product.getStock().getQuantity());
    }

    @Test
    @DisplayName("재고 차감 후 updatedAt 갱신 확인")
    void deduct_stock_updates_timestamp() throws InterruptedException {
        // given
        LocalDateTime beforeUpdate = testProduct.getUpdatedAt();
        Thread.sleep(10);

        // when
        testProduct.deductStock(10);

        // then
        assertTrue(testProduct.getUpdatedAt().isAfter(beforeUpdate));
    }

    @Test
    @DisplayName("재고 복구 후 updatedAt 갱신 확인")
    void restore_stock_updates_timestamp() throws InterruptedException {
        // given
        LocalDateTime beforeUpdate = testProduct.getUpdatedAt();
        Thread.sleep(10);

        // when
        testProduct.restoreStock(10);

        // then
        assertTrue(testProduct.getUpdatedAt().isAfter(beforeUpdate));
    }

    @Test
    @DisplayName("조회수 증가 후 updatedAt 갱신 확인")
    void increase_view_count_updates_timestamp() throws InterruptedException {
        // given
        LocalDateTime beforeUpdate = testProduct.getUpdatedAt();
        Thread.sleep(10);

        // when
        testProduct.increaseViewCount();

        // then
        assertTrue(testProduct.getUpdatedAt().isAfter(beforeUpdate));
    }
}
