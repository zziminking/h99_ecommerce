package h99.ecommerce.repository;

import h99.ecommerce.domain.Order;
import java.util.List;

public interface OrderRepository {

    Order save(Order order);

    Order findOne(Long orderId);

    List<Order> findByUserId(Long userId);
}
