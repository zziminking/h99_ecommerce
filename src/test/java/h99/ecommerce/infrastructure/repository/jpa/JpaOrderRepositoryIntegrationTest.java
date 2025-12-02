package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;

import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.user.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaOrderRepository 통합 테스트")
class JpaOrderRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaOrderRepository orderRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @Test
    @DisplayName("주문 저장")
    void save_order() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(BigDecimal.valueOf(10000))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        Order order = Order.builder()
                .user(savedUser)
                .totalQuantity(2)
                .totalPrice(BigDecimal.valueOf(20000))
                .orderAt(LocalDateTime.now())
                .build();

        // when
        Order saved = orderRepository.save(order);
        flushAndClear();

        // then
        assertThat(order.getOrderId()).isEqualTo(saved.getOrderId());
    }

    @Test
    @DisplayName("주문 조회")
    void findOne_by_id() {
        // given
        User user = User.builder()
                .username("testUser2")
                .point(BigDecimal.valueOf(50000))
                .build();
        User savedUser = userRepository.save(user);

        Order order = Order.builder()
                .user(savedUser)
                .totalQuantity(3)
                .totalPrice(BigDecimal.valueOf(30000))
                .orderAt(LocalDateTime.now())
                .build();
        Order saved = orderRepository.save(order);
        flushAndClear();

        // when
        Order found = orderRepository.findOne(saved.getOrderId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getTotalQuantity()).isEqualTo(3);
        assertThat(found.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    @DisplayName("사용자 ID로 주문 목록 조회")
    void find_orders_by_user_id() {
        // given
        User user = User.builder()
                .username("testUser3")
                .point(BigDecimal.valueOf(100000))
                .build();
        User savedUser = userRepository.save(user);

        Order order1 = Order.builder()
                .user(savedUser)
                .totalQuantity(1)
                .totalPrice(BigDecimal.valueOf(10000))
                .orderAt(LocalDateTime.now())
                .build();
        Order order2 = Order.builder()
                .user(savedUser)
                .totalQuantity(2)
                .totalPrice(BigDecimal.valueOf(20000))
                .orderAt(LocalDateTime.now())
                .build();
        Order order3 = Order.builder()
                .user(savedUser)
                .totalQuantity(3)
                .totalPrice(BigDecimal.valueOf(30000))
                .orderAt(LocalDateTime.now())
                .build();

        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);
        flushAndClear();

        // when
        List<Order> orders = orderRepository.findByUserId(savedUser.getUserId());

        // then
        assertThat(orders).hasSize(3);
        assertThat(orders).extracting("totalQuantity")
                .containsExactlyInAnyOrder(1, 2, 3);
    }
}
