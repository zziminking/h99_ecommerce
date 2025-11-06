package h99.ecommerce.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
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

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private Product stockEmptyProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product(1, "테스트 상품", "설명", new BigDecimal("10000"), new Stock(100), 0, null, null);
        stockEmptyProduct = new Product(2, "품절 상품", "설명", new BigDecimal("20000"), new Stock(0), 0, null, null);
    }

    @Test
    @DisplayName("상품 단건 조회 - 성공 및 조회수 증가")
    void get_product_success() {
        // given
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        Product result = productService.getProduct(1);

        // then
        assertNotNull(result);
        assertEquals(1, result.getProductId());
        assertEquals(1, result.getTotalViewCount()); // 조회수 증가 확인
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("상품 단건 조회 - 상품 없음 실패")
    void get_product_not_found_fail() {
        // given
        when(productRepository.findOne(999)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.getProduct(999));
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
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        boolean result = productService.checkStockAvailable(1);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("재고 존재 여부 확인 - 재고 없음")
    void check_stock_available_false() {
        // given
        when(productRepository.findOne(2)).thenReturn(stockEmptyProduct);

        // when
        boolean result = productService.checkStockAvailable(2);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("재고 충분 여부 확인 - 충분함")
    void check_stock_enough_true() {
        // given
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        boolean result = productService.checkStockEnough(1, 50);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("재고 충분 여부 확인 - 부족함")
    void check_stock_enough_false() {
        // given
        Product lowStockProduct = new Product(3, "재고 적은 상품", "설명", new BigDecimal("5000"), new Stock(10), 0, null, null);
        when(productRepository.findOne(3)).thenReturn(lowStockProduct);

        // when
        boolean result = productService.checkStockEnough(3, 50);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("재고 차감 - 성공")
    void deduct_stock_success() {
        // given
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        productService.deductStock(1, 30);

        // then
        assertEquals(70, testProduct.getStock().getQuantity());
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("재고 차감 - 상품 없음 실패")
    void deduct_stock_product_not_found_fail() {
        // given
        when(productRepository.findOne(999)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.deductStock(999, 10));
    }

    @Test
    @DisplayName("재고 차감 - 품절 상품 실패")
    void deduct_stock_out_of_stock_fail() {
        // given
        when(productRepository.findOne(2)).thenReturn(stockEmptyProduct);

        // when & then
        NotEnoughStockException exception = assertThrows(
                NotEnoughStockException.class,
                () -> productService.deductStock(2, 10)
        );
        assertEquals("품절된 상품입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("재고 차감 - 재고 부족 실패")
    void deduct_stock_not_enough_fail() {
        // given
        Product lowStockProduct = new Product(3, "재고 적은 상품", "설명", new BigDecimal("5000"), new Stock(10), 0, null, null);
        when(productRepository.findOne(3)).thenReturn(lowStockProduct);

        // when & then
        NotEnoughStockException exception = assertThrows(
                NotEnoughStockException.class,
                () -> productService.deductStock(3, 20)
        );
        assertTrue(exception.getMessage().contains("재고가 부족합니다"));
    }

    @Test
    @DisplayName("재고 복구 - 성공")
    void restore_stock_success() {
        // given
        testProduct.deductStock(30); // 재고를 70으로 만듦
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        productService.restoreStock(1, 20);

        // then
        assertEquals(90, testProduct.getStock().getQuantity());
        verify(productRepository).save(testProduct);
    }

    @Test
    @DisplayName("재고 복구 - 상품 없음 실패")
    void restore_stock_product_not_found_fail() {
        // given
        when(productRepository.findOne(999)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.restoreStock(999, 10));
    }
}
