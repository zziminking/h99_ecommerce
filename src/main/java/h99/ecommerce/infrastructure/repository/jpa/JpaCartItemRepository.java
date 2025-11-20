package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.CartItem;
import h99.ecommerce.repository.CartItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCartItemRepository implements CartItemRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem.getCartItemId() == null) {
            em.persist(cartItem);
            return cartItem;
        } else {
            return em.merge(cartItem);
        }
    }

    @Override
    public CartItem findOne(Long cartItemId) {
        return em.find(CartItem.class, cartItemId);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return em.createQuery("SELECT c FROM CartItem c WHERE c.userId = :userId", CartItem.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        try {
            CartItem result = em.createQuery(
                            "SELECT c FROM CartItem c WHERE c.userId = :userId AND c.productId = :productId",
                            CartItem.class)
                    .setParameter("userId", userId)
                    .setParameter("productId", productId)
                    .getSingleResult();
            return Optional.of(result);
        } catch (NoResultException e) {
            return Optional.empty();
        }

    }

    @Override
    public void updateQuantity(Long cartItemId, int newQuantity) {
        CartItem cartItem = em.find(CartItem.class, cartItemId);
        if (cartItem != null) {
            cartItem.updateQuantity(newQuantity);
        }
    }

    @Override
    public void delete(Long cartItemId) {
        em.remove(em.find(CartItem.class, cartItemId));
    }
}
