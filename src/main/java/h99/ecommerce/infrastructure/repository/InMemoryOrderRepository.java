package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Order;
import h99.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Integer, Order> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public int generateId() {
        return idGenerator.getAndIncrement();
    }

    @Override
    public Order save(Order order) {
        store.put(order.getOrderId(), order);
        return order;
    }

    @Override
    public Order findOne(int orderId) {
        return store.get(orderId);
    }

    @Override
    public List<Order> findByUserId(int userId) {
        return store.values().stream()
                .filter(order -> order.getUserId() == userId)
                .toList();
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}
