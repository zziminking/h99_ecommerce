package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.CartItem;
import h99.ecommerce.repository.CartItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryCartItemRepository implements CartItemRepository {

    private final Map<Integer, CartItem> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public int generateId() {
        return idGenerator.getAndIncrement();
    }

    @Override
    public CartItem save(CartItem cartItem) {
        store.put(cartItem.getCartItemId(), cartItem);
        return cartItem;
    }

    @Override
    public CartItem findOne(int cartItemId) {
        return store.get(cartItemId);
    }

    @Override
    public List<CartItem> findByUserId(int userId) {
        return store.values().stream()
                .filter(cartItem -> cartItem.getUserId() == userId)
                .toList();
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(int userId, int productId) {
        return store.values().stream()
                .filter(cartItem -> cartItem.getUserId() == userId && cartItem.getProductId() == productId)
                .findFirst();
    }

    @Override
    public void updateQuantity(int cartItemId, int newQuantity) {
        CartItem cartItem = store.get(cartItemId);
        if (cartItem != null) {
            cartItem.updateQuantity(newQuantity);
        }
    }

    @Override
    public void delete(int cartItemId) {
        store.remove(cartItemId);
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}
