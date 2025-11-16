package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.CartItem;
import h99.ecommerce.repository.CartItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class InMemoryCartItemRepository implements CartItemRepository {

    private final Map<Long, CartItem> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public CartItem save(CartItem cartItem) {
        // ID가 없으면 새로 생성 (JPA의 GeneratedValue 동작 모방)
        if (cartItem.getCartItemId() == null) {
            Long newId = (long) idGenerator.getAndIncrement();
            CartItem newCartItem = new CartItem(
                    newId,
                    cartItem.getUserId(),
                    cartItem.getProductId(),
                    cartItem.getQuantity()
            );
            store.put(newId, newCartItem);
            return newCartItem;
        }
        store.put(cartItem.getCartItemId(), cartItem);
        return cartItem;
    }

    @Override
    public CartItem findOne(Long cartItemId) {
        return store.get(cartItemId);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return store.values().stream()
                .filter(cartItem -> cartItem.getUserId().equals(userId))
                .toList();
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
                .filter(cartItem -> cartItem.getUserId().equals(userId) && cartItem.getProductId().equals(productId))
                .findFirst();
    }

    @Override
    public void updateQuantity(Long cartItemId, int newQuantity) {
        CartItem cartItem = store.get(cartItemId);
        if (cartItem != null) {
            cartItem.updateQuantity(newQuantity);
        }
    }

    @Override
    public void delete(Long cartItemId) {
        store.remove(cartItemId);
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}
