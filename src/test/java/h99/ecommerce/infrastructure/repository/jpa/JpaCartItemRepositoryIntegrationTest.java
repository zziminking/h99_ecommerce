package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

import h99.ecommerce.domain.cartitem.CartItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaCartItemRepository 통합 테스트")
class JpaCartItemRepositoryIntegrationTest extends BaseJpaRepositoryTest{

    @Autowired
    private JpaCartItemRepository cartItemRepository;

    @Test
    @DisplayName("장바구니 아이템 저장")
    void save_cart_item() {
        // given
        CartItem cartItem = CartItem.builder()
                .userId(1L)
                .productId(1L)
                .quantity(1)
                .build();

        // when
        CartItem saved = cartItemRepository.save(cartItem);
        flushAndClear();

        // then
        assertThat(cartItem.getCartItemId()).isEqualTo(saved.getCartItemId());
    }

    @Test
    @DisplayName("사용자 ID로 장바구니 아이템 조회")
    void find_cart_item_by_user_id() {
        // given
        Long userId = 1L;

        CartItem item1 = CartItem.builder()
                .userId(userId)
                .productId(100L)
                .quantity(2)
                .build();
        CartItem item2 = CartItem.builder()
                .userId(userId)
                .productId(200L)
                .quantity(3)
                .build();
        CartItem item3 = CartItem.builder()
                .userId(userId)
                .productId(300L)
                .quantity(1)
                .build();

        cartItemRepository.save(item1);
        cartItemRepository.save(item2);
        cartItemRepository.save(item3);
        flushAndClear();

        // when
        List<CartItem> items = cartItemRepository.findByUserId(userId);

        assertThat(items).extracting("productId")
                .containsExactlyInAnyOrder(100L, 200L, 300L);
    }

    @Test
    @DisplayName("장바구니 수량 변경")
    void update_quantity() {
        // given
        CartItem cartItem = CartItem.builder()
                .userId(1L)
                .productId(100L)
                .quantity(3)
                .build();
        CartItem savedItem = cartItemRepository.save(cartItem);
        flushAndClear();

        // when
        cartItemRepository.updateQuantity(savedItem.getCartItemId(), 10);
        flushAndClear();

        // then
        CartItem updatedItem = cartItemRepository.findOne(savedItem.getCartItemId());
        assertThat(updatedItem.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("장바구니 아이템 삭제")
    void delete_cart_item() {
        // given
        CartItem cartItem = CartItem.builder()
                .userId(1L)
                .productId(100L)
                .quantity(3)
                .build();
        CartItem savedItem = cartItemRepository.save(cartItem);
        flushAndClear();

        // when
        cartItemRepository.delete(savedItem.getCartItemId());
        flushAndClear();

        // then
        CartItem deletedItem = cartItemRepository.findOne(savedItem.getCartItemId());
        assertNull(deletedItem);
    }
}
