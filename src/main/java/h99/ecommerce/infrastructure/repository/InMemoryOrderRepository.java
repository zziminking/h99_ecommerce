package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Order;
import h99.ecommerce.repository.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        store.put(order.getOrderId(), order);
        return order;
    }

    @Override
    public Order findOne(Long orderId) {
        return store.get(orderId);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return store.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
