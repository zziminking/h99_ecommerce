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

import static org.junit.jupiter.api.Assertions.*;

public class OrderItemTest {

    private OrderItem orderItem;
    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        orderItem = new OrderItem(
                1L, order, product, 5, OrderStatus.PENDING,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000"),
                new BigDecimal("10000"), null, null
        );
    }

    @Test
    @DisplayName("주문 아이템 생성 - 성공")
    void create_order_item_success() {
        // given & when
        OrderItem item = new OrderItem(
                2L, order, product, 3, OrderStatus.PENDING,
                null, null, null,
                new BigDecimal("5000"), null, null
        );

        // then
        assertEquals(2, item.getOrderItemId());
        assertEquals(100, item.getOrder());
        assertEquals(2, item.getProduct());
        assertEquals(3, item.getQuantity());
        assertEquals(OrderStatus.PENDING, item.getStatus());
        assertEquals(BigDecimal.ZERO, item.getTotalAmount());
        assertEquals(BigDecimal.ZERO, item.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, item.getFinalAmount());
        assertEquals(new BigDecimal("5000"), item.getPrice());
        assertNotNull(item.getCreatedAt());
        assertNotNull(item.getUpdatedAt());
    }

    @Test
    @DisplayName("주문 아이템 생성 - 수량 0 실패")
    void create_order_item_with_zero_quantity_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new OrderItem(2L, order, product, 0, OrderStatus.PENDING,
                        null, null, null, new BigDecimal("5000"), null, null)
        );
    }

    @Test
    @DisplayName("주문 아이템 생성 - 음수 수량 실패")
    void create_order_item_with_negative_quantity_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new OrderItem(2L, order, product, -1, OrderStatus.PENDING,
                        null, null, null, new BigDecimal("5000"), null, null)
        );
    }

    @Test
    @DisplayName("주문 아이템 생성 - 가격 null 실패")
    void create_order_item_with_null_price_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new OrderItem(2L, order, product, 3, OrderStatus.PENDING,
                        null, null, null, null, null, null)
        );
    }

    @Test
    @DisplayName("주문 아이템 생성 - 음수 가격 실패")
    void create_order_item_with_negative_price_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new OrderItem(2L, order, product, 3, OrderStatus.PENDING,
                        null, null, null, new BigDecimal("-1000"), null, null)
        );
    }

    @Test
    @DisplayName("총 금액 계산 - 성공")
    void calculate_total_amount_success() {
        // given
        OrderItem item = new OrderItem(
                2L, order, product, 3, OrderStatus.PENDING,
                null, null, null,
                new BigDecimal("5000"), null, null
        );

        // when
        item.calculateTotalAmount();

        // then
        assertEquals(new BigDecimal("15000"), item.getTotalAmount());
    }

    @Test
    @DisplayName("최종 금액 계산 - 할인 없음")
    void calculate_final_amount_no_discount() {
        // when
        orderItem.calculateFinalAmount();

        // then
        assertEquals(new BigDecimal("50000"), orderItem.getFinalAmount());
    }

    @Test
    @DisplayName("최종 금액 계산 - 할인 적용")
    void calculate_final_amount_with_discount() {
        // given
        OrderItem item = new OrderItem(
                2L, order, product, 3, OrderStatus.PENDING,
                new BigDecimal("15000"), new BigDecimal("3000"), null,
                new BigDecimal("5000"), null, null
        );

        // when
        item.calculateFinalAmount();

        // then
        assertEquals(new BigDecimal("12000"), item.getFinalAmount());
    }

    @Test
    @DisplayName("최종 금액 계산 - 할인이 총액보다 큰 경우 0원")
    void calculate_final_amount_discount_greater_than_total() {
        // given
        OrderItem item = new OrderItem(
                2L, order, product, 3, OrderStatus.PENDING,
                new BigDecimal("15000"), new BigDecimal("20000"), null,
                new BigDecimal("5000"), null, null
        );

        // when
        item.calculateFinalAmount();

        // then
        assertEquals(BigDecimal.ZERO, item.getFinalAmount());
    }

    @Test
    @DisplayName("할인 적용 - 성공")
    void apply_discount_success() {
        // given
        BigDecimal discountAmount = new BigDecimal("5000");

        // when
        orderItem.applyDiscount(discountAmount);

        // then
        assertEquals(discountAmount, orderItem.getDiscountAmount());
        assertEquals(new BigDecimal("45000"), orderItem.getFinalAmount());
    }

    @Test
    @DisplayName("할인 적용 - 음수 할인 실패")
    void apply_discount_with_negative_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                orderItem.applyDiscount(new BigDecimal("-1000"))
        );
    }

    @Test
    @DisplayName("할인 적용 - null 할인 실패")
    void apply_discount_with_null_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                orderItem.applyDiscount(null)
        );
    }

    @Test
    @DisplayName("상태 변경 - 성공")
    void change_status_success() {
        // when
        orderItem.changeStatus(OrderStatus.COMPLETED);

        // then
        assertEquals(OrderStatus.COMPLETED, orderItem.getStatus());
    }

    @Test
    @DisplayName("상태 변경 - null 상태 실패")
    void change_status_with_null_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                orderItem.changeStatus(null)
        );
    }

    @Test
    @DisplayName("완료 상태 확인 - true")
    void is_completed_true() {
        // given
        orderItem.changeStatus(OrderStatus.COMPLETED);

        // when
        boolean result = orderItem.isCompleted();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("완료 상태 확인 - false")
    void is_completed_false() {
        // when
        boolean result = orderItem.isCompleted();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("취소 상태 확인 - true")
    void is_canceled_true() {
        // given
        orderItem.changeStatus(OrderStatus.CANCELED);

        // when
        boolean result = orderItem.isCanceled();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("취소 상태 확인 - false")
    void is_canceled_false() {
        // when
        boolean result = orderItem.isCanceled();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("대기 상태 확인 - true")
    void is_pending_true() {
        // when
        boolean result = orderItem.isPending();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("대기 상태 확인 - false")
    void is_pending_false() {
        // given
        orderItem.changeStatus(OrderStatus.COMPLETED);

        // when
        boolean result = orderItem.isPending();

        // then
        assertFalse(result);
    }
}
