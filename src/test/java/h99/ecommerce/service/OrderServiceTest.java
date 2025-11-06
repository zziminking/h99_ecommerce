package h99.ecommerce.service;

import h99.ecommerce.domain.*;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.CartItemRepository;
import h99.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private OrderService orderService;

    private CartItem cartItem;
    private Product product;

    @BeforeEach
    void setUp() {
        cartItem = new CartItem(1, 100, 1, 3);
        product = new Product(1, "테스트 상품", "설명", new BigDecimal("10000"), new Stock(100), 0, null, null);
    }

    @Test
    @DisplayName("주문 생성 - 성공")
    void create_order_success() {
        // given
        int userId = 100;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(orderRepository.generateId()).thenReturn(1);
        when(productService.getProduct(1)).thenReturn(product);
        doNothing().when(productService).deductStock(anyInt(), anyInt());
        doNothing().when(paymentService).processPayment(anyInt(), anyInt(), any(BigDecimal.class), any());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order order = orderService.createOrder(userId, null);

        // then
        assertNotNull(order);
        assertEquals(userId, order.getUserId());
        assertEquals(1, order.getOrderItems().size());
        verify(orderRepository).save(any(Order.class));
        verify(paymentService).processPayment(eq(userId), eq(1), any(BigDecimal.class), isNull());
        verify(cartItemRepository).delete(cartItem.getCartItemId());
    }

    @Test
    @DisplayName("주문 생성 - 쿠폰 적용")
    void create_order_with_coupon_success() {
        // given
        int userId = 100;
        Integer userCouponId = 1;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(orderRepository.generateId()).thenReturn(1);
        when(productService.getProduct(1)).thenReturn(product);
        doNothing().when(productService).deductStock(anyInt(), anyInt());
        doNothing().when(paymentService).processPayment(anyInt(), anyInt(), any(BigDecimal.class), any());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order order = orderService.createOrder(userId, userCouponId);

        // then
        assertNotNull(order);
        verify(paymentService).processPayment(eq(userId), eq(1), any(BigDecimal.class), eq(userCouponId));
    }

    @Test
    @DisplayName("주문 생성 - 장바구니 비어있음 실패")
    void create_order_empty_cart_fail() {
        // given
        int userId = 100;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(userId, null)
        );
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 생성 - 재고 부족 시 롤백")
    void create_order_stock_shortage_rollback() {
        // given
        int userId = 100;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(orderRepository.generateId()).thenReturn(1);
        when(productService.getProduct(1)).thenReturn(product);
        doThrow(new NotEnoughStockException("재고 부족")).when(productService).deductStock(1, 3);

        // when & then
        assertThrows(NotEnoughStockException.class, () ->
                orderService.createOrder(userId, null)
        );
        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentService, never()).processPayment(anyInt(), anyInt(), any(BigDecimal.class), any());
    }

    @Test
    @DisplayName("주문 생성 - 결제 실패 시 재고 복구")
    void create_order_payment_fail_restore_stock() {
        // given
        int userId = 100;
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(orderRepository.generateId()).thenReturn(1);
        when(productService.getProduct(1)).thenReturn(product);
        doNothing().when(productService).deductStock(anyInt(), anyInt());
        doThrow(new IllegalStateException("결제 실패")).when(paymentService)
                .processPayment(anyInt(), anyInt(), any(BigDecimal.class), any());

        // when & then
        assertThrows(IllegalStateException.class, () ->
                orderService.createOrder(userId, null)
        );
        verify(productService).restoreStock(1, 3);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 조회 - 성공")
    void get_order_success() {
        // given
        int orderId = 1;
        Order order = Order.builder()
                .orderId(orderId)
                .userId(100)
                .totalQuantity(3)
                .totalPrice(new BigDecimal("30000"))
                .build();
        when(orderRepository.findOne(orderId)).thenReturn(order);

        // when
        Order result = orderService.getOrder(orderId);

        // then
        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
        verify(orderRepository).findOne(orderId);
    }

    @Test
    @DisplayName("주문 조회 - 주문 없음 실패")
    void get_order_not_found_fail() {
        // given
        int orderId = 999;
        when(orderRepository.findOne(orderId)).thenReturn(null);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                orderService.getOrder(orderId)
        );
    }

    @Test
    @DisplayName("사용자별 주문 목록 조회 - 성공")
    void get_orders_by_user_id_success() {
        // given
        int userId = 100;
        Order order1 = Order.builder().orderId(1).userId(userId).build();
        Order order2 = Order.builder().orderId(2).userId(userId).build();
        when(orderRepository.findByUserId(userId)).thenReturn(Arrays.asList(order1, order2));

        // when
        var orders = orderService.getOrdersByUserId(userId);

        // then
        assertEquals(2, orders.size());
        verify(orderRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("사용자별 주문 목록 조회 - 빈 목록")
    void get_orders_by_user_id_empty() {
        // given
        int userId = 100;
        when(orderRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // when
        var orders = orderService.getOrdersByUserId(userId);

        // then
        assertTrue(orders.isEmpty());
    }
}
