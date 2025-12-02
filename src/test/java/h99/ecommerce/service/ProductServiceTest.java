package h99.ecommerce.service;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductStatistics;
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
import static org.mockito.Mockito.*;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductStatistics;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.domain.product.ProductRepository;
import h99.ecommerce.domain.product.ProductStatisticsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductStatisticsRepository productStatisticsRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private Product stockEmptyProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product(1L, "테스트 상품", "설명", new BigDecimal("10000"), new Stock(100), 0, null, null);
        stockEmptyProduct = new Product(2L, "품절 상품", "설명", new BigDecimal("20000"), new Stock(0), 0, null, null);
    }

    @Test
    @DisplayName("상품 단건 조회 - 성공 (캐시)")
    void get_product_success() {
        // given
        when(productRepository.findOne(1L)).thenReturn(testProduct);

        // when
        Product result = productService.getProduct(1L);

        // then
        assertNotNull(result);
        assertEquals(1, result.getProductId());
        assertEquals(0, result.getTotalViewCount()); // 캐시만 하고 조회수는 증가하지 않음
        verify(productRepository, times(1)).findOne(1L);
        verify(productRepository, never()).save(any());
        verify(productStatisticsRepository, never()).save(any());
    }


    @Test
    @DisplayName("상품 단건 조회 - 상품 없음 실패")
    void get_product_not_found_fail() {
        // given
        when(productRepository.findOne(999L)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.getProduct(999L));
    }

    @Test
    @DisplayName("상품 전체 조회 - 성공")
    void get_all_products_success() {
        // given
        when(productRepository.findAll()).thenReturn(Arrays.asList(testProduct, stockEmptyProduct));

        // when
        List<Product> result = productService.getAllProducts();

        // then
        assertEquals(2, result.size());
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("재고 존재 여부 확인 - 재고 있음")
    void check_stock_available_true() {
        // given
        when(productRepository.findOne(1L)).thenReturn(testProduct);

        // when
        boolean result = productService.checkStockAvailable(1L);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("재고 존재 여부 확인 - 재고 없음")
    void check_stock_available_false() {
        // given
        when(productRepository.findOne(2L)).thenReturn(stockEmptyProduct);

        // when
        boolean result = productService.checkStockAvailable(2L);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("재고 충분 여부 확인 - 충분함")
    void check_stock_enough_true() {
        // given
        when(productRepository.findOne(1L)).thenReturn(testProduct);

        // when
        boolean result = productService.checkStockEnough(1L, 50);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("재고 충분 여부 확인 - 부족함")
    void check_stock_enough_false() {
        // given
        Product lowStockProduct = new Product(3L, "재고 적은 상품", "설명", new BigDecimal("5000"), new Stock(10), 0, null, null);
        when(productRepository.findOne(3L)).thenReturn(lowStockProduct);

        // when
        boolean result = productService.checkStockEnough(3L, 50);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("재고 차감 - 성공")
    void deduct_stock_success() {
        // given
        when(productRepository.findOne(1L)).thenReturn(testProduct);

        // when
        productService.deductStock(1L, 30);

        // then
        assertEquals(70, testProduct.getStock().getQuantity());
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("재고 차감 - 상품 없음 실패")
    void deduct_stock_product_not_found_fail() {
        // given
        when(productRepository.findOne(999L)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.deductStock(999L, 10));
    }

    @Test
    @DisplayName("재고 차감 - 품절 상품 실패")
    void deduct_stock_out_of_stock_fail() {
        // given
        when(productRepository.findOne(2L)).thenReturn(stockEmptyProduct);

        // when & then
        NotEnoughStockException exception = assertThrows(
                NotEnoughStockException.class,
                () -> productService.deductStock(2L, 10)
        );
        assertEquals("품절된 상품입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("재고 차감 - 재고 부족 실패")
    void deduct_stock_not_enough_fail() {
        // given
        Product lowStockProduct = new Product(3L, "재고 적은 상품", "설명", new BigDecimal("5000"), new Stock(10), 0, null, null);
        when(productRepository.findOne(3L)).thenReturn(lowStockProduct);

        // when & then
        NotEnoughStockException exception = assertThrows(
                NotEnoughStockException.class,
                () -> productService.deductStock(3L, 20)
        );
        assertTrue(exception.getMessage().contains("재고가 부족합니다"));
    }

    @Test
    @DisplayName("재고 복구 - 성공")
    void restore_stock_success() {
        // given
        testProduct.deductStock(30); // 재고를 70으로 만듦
        when(productRepository.findOne(1L)).thenReturn(testProduct);

        // when
        productService.restoreStock(1L, 20);

        // then
        assertEquals(90, testProduct.getStock().getQuantity());
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("재고 복구 - 상품 없음 실패")
    void restore_stock_product_not_found_fail() {
        // given
        when(productRepository.findOne(999L)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.restoreStock(999L, 10));
    }
}
