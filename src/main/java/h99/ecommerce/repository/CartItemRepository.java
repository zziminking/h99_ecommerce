package h99.ecommerce.repository;

import h99.ecommerce.domain.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    CartItem findOne(Long cartItemId);

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void updateQuantity(Long cartItemId, int newQuantity);

    void delete(Long cartItemId);
}
