package h99.ecommerce.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import h99.ecommerce.domain.CartItem;
import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.dto.CartItemDto;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.CartItemRepository;
import h99.ecommerce.repository.ProductRepository;
import h99.ecommerce.request.CartAddRequest;
import java.math.BigDecimal;
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
public class CartItemServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartItemService cartItemService;

    private Product testProduct;
    private CartItem testCartItem;

    @BeforeEach
    void setUp() {
        testProduct = new Product(1, "테스트 상품", "설명", new BigDecimal("10000"), new Stock(100), 0, null, null);
        testCartItem = new CartItem(1, 100, 1, 5);
    }

    @Test
    @DisplayName("장바구니 조회 - 성공")
    void get_cart_success() {
        // given
        int userId = 100;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(testCartItem));
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        List<CartItemDto> result = cartItemService.getCart(userId);

        // then
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getCartItemId());
        assertEquals(5, result.get(0).getQuantity());
        verify(cartItemRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("장바구니 추가 - 새 상품")
    void add_cart_item_new_product_success() {
        // given
        CartAddRequest request = new CartAddRequest(100, 1, 3);
        when(productRepository.findOne(1)).thenReturn(testProduct);
        when(cartItemRepository.findByUserIdAndProductId(100, 1)).thenReturn(Optional.empty());
        when(cartItemRepository.generateId()).thenReturn(2);

        // when
        cartItemService.addCartItem(request);

        // then
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("장바구니 추가 - 기존 상품 수량 증가")
    void add_cart_item_existing_product_success() {
        // given
        CartAddRequest request = new CartAddRequest(100, 1, 3);
        when(productRepository.findOne(1)).thenReturn(testProduct);
        when(cartItemRepository.findByUserIdAndProductId(100, 1)).thenReturn(Optional.of(testCartItem));

        // when
        cartItemService.addCartItem(request);

        // then
        verify(cartItemRepository).updateQuantity(1, 8);
    }

    @Test
    @DisplayName("장바구니 추가 - 상품 없음 실패")
    void add_cart_item_product_not_found_fail() {
        // given
        CartAddRequest request = new CartAddRequest(100, 999, 3);
        when(productRepository.findOne(999)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> cartItemService.addCartItem(request));
    }

    @Test
    @DisplayName("장바구니 추가 - 품절 상품 실패")
    void add_cart_item_out_of_stock_fail() {
        // given
        Product stockEmptyProduct = new Product(2, "품절 상품", "설명", new BigDecimal("10000"), new Stock(0), 0, null, null);
        CartAddRequest request = new CartAddRequest(100, 2, 3);
        when(productRepository.findOne(2)).thenReturn(stockEmptyProduct);

        // when & then
        assertThrows(NotEnoughStockException.class, () -> cartItemService.addCartItem(request));
    }

    @Test
    @DisplayName("장바구니 추가 - 재고 부족 실패")
    void add_cart_item_not_enough_stock_fail() {
        // given
        Product lowStockProduct = new Product(3, "재고 적은 상품", "설명", new BigDecimal("10000"), new Stock(2), 0, null, null);
        CartAddRequest request = new CartAddRequest(100, 3, 5);
        when(productRepository.findOne(3)).thenReturn(lowStockProduct);
        when(cartItemRepository.findByUserIdAndProductId(100, 3)).thenReturn(Optional.empty());

        // when & then
        assertThrows(NotEnoughStockException.class, () -> cartItemService.addCartItem(request));
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 성공")
    void update_cart_item_quantity_success() {
        // given
        when(cartItemRepository.findOne(1)).thenReturn(testCartItem);
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        cartItemService.updateCartItemQuantity(1, 10);

        // then
        verify(cartItemRepository).updateQuantity(1, 10);
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 장바구니 아이템 없음 실패")
    void update_cart_item_quantity_not_found_fail() {
        // given
        when(cartItemRepository.findOne(999)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> cartItemService.updateCartItemQuantity(999, 10));
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 0 이하 수량 실패")
    void update_cart_item_quantity_invalid_quantity_fail() {
        // given
        when(cartItemRepository.findOne(1)).thenReturn(testCartItem);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> cartItemService.updateCartItemQuantity(1, 0));
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 재고 부족 실패")
    void update_cart_item_quantity_not_enough_stock_fail() {
        // given
        Product lowStockProduct = new Product(1, "재고 적은 상품", "설명", new BigDecimal("10000"), new Stock(5), 0, null, null);
        when(cartItemRepository.findOne(1)).thenReturn(testCartItem);
        when(productRepository.findOne(1)).thenReturn(lowStockProduct);

        // when & then
        assertThrows(NotEnoughStockException.class, () -> cartItemService.updateCartItemQuantity(1, 10));
    }

    @Test
    @DisplayName("장바구니 아이템 삭제 - 성공")
    void remove_cart_item_success() {
        // given
        when(cartItemRepository.findOne(1)).thenReturn(testCartItem);

        // when
        cartItemService.removeCartItem(1);

        // then
        verify(cartItemRepository).delete(1);
    }

    @Test
    @DisplayName("장바구니 아이템 삭제 - 아이템 없음 실패")
    void remove_cart_item_not_found_fail() {
        // given
        when(cartItemRepository.findOne(999)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> cartItemService.removeCartItem(999));
    }

    @Test
    @DisplayName("장바구니 총액 계산 - 성공")
    void get_cart_total_success() {
        // given
        int userId = 100;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(testCartItem));
        when(productRepository.findOne(1)).thenReturn(testProduct);

        // when
        BigDecimal total = cartItemService.getCartTotal(userId);

        // then
        assertEquals(new BigDecimal("50000"), total);
    }
}
