package h99.ecommerce.repository;

import h99.ecommerce.domain.Order;
import java.util.List;

public interface OrderRepository {

    int generateId();

    Order save(Order order);

    Order findOne(int orderId);

    List<Order> findByUserId(int userId);
}
