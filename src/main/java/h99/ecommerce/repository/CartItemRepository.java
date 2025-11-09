package h99.ecommerce.repository;

import h99.ecommerce.domain.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    int generateId();

    CartItem save(CartItem cartItem);

    CartItem findOne(int cartItemId);

    List<CartItem> findByUserId(int userId);

    Optional<CartItem> findByUserIdAndProductId(int userId, int productId);

    void updateQuantity(int cartItemId, int newQuantity);

    void delete(int cartItemId);
}
