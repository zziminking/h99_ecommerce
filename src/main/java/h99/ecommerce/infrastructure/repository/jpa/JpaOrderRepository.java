package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.order.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaOrderRepository implements OrderRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public Order save(Order order) {
        if (order.getOrderId() == null) {
            em.persist(order);
            return order;
        } else {
            return em.merge(order);
        }
    }

    @Override
    public Order findOne(Long orderId) {
        return em.find(Order.class, orderId);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return em.createQuery("SELECT o FROM Order o WHERE o.user.userId = :userId", Order.class)
                .setParameter("userId", userId)
                .getResultList();

    }
}
