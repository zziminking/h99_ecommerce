package h99.ecommerce.domain.order;

import h99.ecommerce.domain.order.Order;
import java.util.List;

public interface OrderRepository {

    Order save(Order order);

    Order findOne(Long orderId);

    List<Order> findByUserId(Long userId);
}
