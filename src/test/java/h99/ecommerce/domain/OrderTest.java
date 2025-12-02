package h99.ecommerce.domain;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.order.OrderItem;
import h99.ecommerce.domain.order.OrderStatus;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.domain.cartitem.CartItem;
import h99.ecommerce.domain.point.Point;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {

    private User user;
    private Order order;
    private Product product;
    private OrderItem orderItem1;
    private OrderItem orderItem2;

    @BeforeEach
    void setUp() {
        order = new Order(1L, user, 0, BigDecimal.ZERO, null, null, null, new ArrayList<>());

        orderItem1 = new OrderItem(
                1L, order, product, 3, OrderStatus.PENDING,
                new BigDecimal("30000"), BigDecimal.ZERO, new BigDecimal("30000"),
                new BigDecimal("10000"), null, null
        );

        orderItem2 = new OrderItem(
                2L, order, product, 2, OrderStatus.PENDING,
                new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("40000"),
                new BigDecimal("20000"), null, null
        );
    }

    @Test
    @DisplayName("주문 생성 - 성공")
    void create_order_success() {
        // given & when
        Order newOrder = new Order(2L, user, 0, null, null, null, null, null);

        // then
        assertEquals(2, newOrder.getOrderId());
        assertEquals(200, newOrder.getUser());
        assertEquals(BigDecimal.ZERO, newOrder.getTotalPrice());
        assertNotNull(newOrder.getOrderAt());
        assertNotNull(newOrder.getCreatedAt());
        assertNotNull(newOrder.getUpdatedAt());
        assertNotNull(newOrder.getOrderItems());
        assertTrue(newOrder.getOrderItems().isEmpty());
    }

    @Test
    @DisplayName("주문 아이템 추가 - 성공")
    void add_order_item_success() {
        // when
        order.addOrderItem(orderItem1);

        // then
        assertEquals(1, order.getOrderItems().size());
        assertEquals(orderItem1, order.getOrderItems().get(0));
    }

    @Test
    @DisplayName("주문 아이템 추가 - 여러 개")
    void add_multiple_order_items_success() {
        // when
        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        // then
        assertEquals(2, order.getOrderItems().size());
    }

    @Test
    @DisplayName("주문 아이템 추가 - null 실패")
    void add_order_item_with_null_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                order.addOrderItem(null)
        );
    }

    @Test
    @DisplayName("주문 총 금액 계산 - 성공")
    void calculate_total_price_success() {
        // given
        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        // when
        order.calculateTotalPrice();

        // then
        assertEquals(new BigDecimal("70000"), order.getTotalPrice());
    }

    @Test
    @DisplayName("주문 총 금액 계산 - 아이템 없음")
    void calculate_total_price_no_items() {
        // when
        order.calculateTotalPrice();

        // then
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());
    }

    @Test
    @DisplayName("주문 총 수량 계산 - 성공")
    void calculate_total_quantity_success() {
        // given
        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        // when
        order.calculateTotalQuantity();

        // then
        assertEquals(5, order.getTotalQuantity());
    }

    @Test
    @DisplayName("주문 총 수량 계산 - 아이템 없음")
    void calculate_total_quantity_no_items() {
        // when
        order.calculateTotalQuantity();

        // then
        assertEquals(0, order.getTotalQuantity());
    }

    @Test
    @DisplayName("주문 완료 여부 - 모두 완료")
    void is_completed_all_items_completed() {
        // given
        orderItem1.changeStatus(OrderStatus.COMPLETED);
        orderItem2.changeStatus(OrderStatus.COMPLETED);
        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        // when
        boolean result = order.isCompleted();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("주문 완료 여부 - 일부 미완료")
    void is_completed_some_items_not_completed() {
        // given
        orderItem1.changeStatus(OrderStatus.COMPLETED);
        orderItem2.changeStatus(OrderStatus.PENDING);
        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        // when
        boolean result = order.isCompleted();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("주문 완료 여부 - 모두 미완료")
    void is_completed_all_items_not_completed() {
        // given
        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        // when
        boolean result = order.isCompleted();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("주문 완료 여부 - 아이템 없음")
    void is_completed_no_items() {
        // when
        boolean result = order.isCompleted();

        // then
        assertTrue(result); // 빈 스트림은 allMatch가 true 반환
    }
}
