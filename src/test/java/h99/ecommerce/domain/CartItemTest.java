package h99.ecommerce.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CartItemTest {

    @Test
    @DisplayName("장바구니 아이템 생성 - 성공")
    void create_cart_item_success() {
        // given
        int cartItemId = 1;
        int userId = 100;
        int productId = 200;
        int quantity = 5;

        // when
        CartItem cartItem = new CartItem(cartItemId, userId, productId, quantity);

        // then
        assertEquals(cartItemId, cartItem.getCartItemId());
        assertEquals(userId, cartItem.getUserId());
        assertEquals(productId, cartItem.getProductId());
        assertEquals(quantity, cartItem.getQuantity());
    }

    @Test
    @DisplayName("장바구니 아이템 생성 시 수량이 0이면 예외 발생")
    void create_cart_item_with_zero_quantity_fail() {
        // given
        int cartItemId = 1;
        int userId = 100;
        int productId = 200;
        int quantity = 0;

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CartItem(cartItemId, userId, productId, quantity)
        );
        assertEquals("수량은 1 이상이어야 합니다", exception.getMessage());
    }

    @Test
    @DisplayName("장바구니 아이템 생성 시 수량이 음수면 예외 발생")
    void create_cart_item_with_negative_quantity_fail() {
        // given
        int cartItemId = 1;
        int userId = 100;
        int productId = 200;
        int quantity = -5;

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CartItem(cartItemId, userId, productId, quantity)
        );
        assertEquals("수량은 1 이상이어야 합니다", exception.getMessage());
    }

    @Test
    @DisplayName("수량 업데이트 - 성공")
    void update_quantity_success() {
        // given
        CartItem cartItem = new CartItem(1, 100, 200, 5);
        int newQuantity = 10;

        // when
        cartItem.updateQuantity(newQuantity);

        // then
        assertEquals(newQuantity, cartItem.getQuantity());
    }

    @Test
    @DisplayName("수량 업데이트 시 0 이하면 예외 발생")
    void update_quantity_with_zero_fail() {
        // given
        CartItem cartItem = new CartItem(1, 100, 200, 5);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cartItem.updateQuantity(0)
        );
        assertEquals("수량은 1 이상이어야 합니다", exception.getMessage());
    }

    @Test
    @DisplayName("수량 업데이트 시 음수면 예외 발생")
    void update_quantity_with_negative_fail() {
        // given
        CartItem cartItem = new CartItem(1, 100, 200, 5);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cartItem.updateQuantity(-3)
        );
        assertEquals("수량은 1 이상이어야 합니다", exception.getMessage());
    }

    @Test
    @DisplayName("수량 추가 - 성공")
    void add_quantity_success() {
        // given
        CartItem cartItem = new CartItem(1, 100, 200, 5);
        int additionalQuantity = 3;

        // when
        cartItem.addQuantity(additionalQuantity);

        // then
        assertEquals(8, cartItem.getQuantity());
    }

    @Test
    @DisplayName("수량 추가 시 0 이하면 예외 발생")
    void add_quantity_with_zero_fail() {
        // given
        CartItem cartItem = new CartItem(1, 100, 200, 5);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cartItem.addQuantity(0)
        );
        assertEquals("추가할 수량은 1 이상이어야 합니다", exception.getMessage());
    }

    @Test
    @DisplayName("수량 추가 시 음수면 예외 발생")
    void add_quantity_with_negative_fail() {
        // given
        CartItem cartItem = new CartItem(1, 100, 200, 5);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cartItem.addQuantity(-2)
        );
        assertEquals("추가할 수량은 1 이상이어야 합니다", exception.getMessage());
    }

    @Test
    @DisplayName("Builder 패턴으로 생성 - 성공")
    void create_with_builder_success() {
        // given & when
        CartItem cartItem = CartItem.builder()
                .cartItemId(1)
                .userId(100)
                .productId(200)
                .quantity(5)
                .build();

        // then
        assertEquals(1, cartItem.getCartItemId());
        assertEquals(100, cartItem.getUserId());
        assertEquals(200, cartItem.getProductId());
        assertEquals(5, cartItem.getQuantity());
    }
}
