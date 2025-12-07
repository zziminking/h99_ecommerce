package h99.ecommerce.service;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductStatistics;
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

import h99.ecommerce.domain.*;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.domain.cartitem.CartItemRepository;
import h99.ecommerce.domain.order.OrderRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
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

    @Mock
    private h99.ecommerce.domain.user.UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private CartItem cartItem;
    private Product product;

    @BeforeEach
    void setUp() {
        cartItem = new CartItem(1L, 100L, 1L, 3);
        product = new Product(1L, "테스트 상품", "설명", new BigDecimal("10000"), new Stock(100), 0, null, null);
    }

    @Test
    @DisplayName("주문 생성 - 성공")
    void create_order_success() {
        // given
        Long userId = 100L;
        User mockUser = User.builder()
                .userId(userId)
                .username("testUser")
                .point(new BigDecimal("100000"))
                .build();
        when(userRepository.findOne(userId)).thenReturn(mockUser);
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(productService.getProduct(1L)).thenReturn(product);
        doNothing().when(productService).deductStock(anyLong(), anyInt());
        doNothing().when(productService).updateOrderStatistics(anyLong(), anyInt());
        doNothing().when(paymentService).processPayment(anyLong(), any(), any(BigDecimal.class), any());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order order = orderService.createOrder(userId, null);

        // then
        assertNotNull(order);
        assertEquals(userId, order.getUserId());
        assertEquals(1, order.getOrderItems().size());
        verify(orderRepository).save(any(Order.class));
        verify(paymentService).processPayment(eq(userId), any(), any(BigDecimal.class), isNull());
        verify(cartItemRepository).delete(cartItem.getCartItemId());
    }

    @Test
    @DisplayName("주문 생성 - 쿠폰 적용")
    void create_order_with_coupon_success() {
        // given
        Long userId = 100L;
        Long userCouponId = 1L;
        User mockUser = User.builder()
                .userId(userId)
                .username("testUser")
                .point(new BigDecimal("100000"))
                .build();
        when(userRepository.findOne(userId)).thenReturn(mockUser);
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(productService.getProduct(1L)).thenReturn(product);
        doNothing().when(productService).deductStock(anyLong(), anyInt());
        doNothing().when(productService).updateOrderStatistics(anyLong(), anyInt());
        doNothing().when(paymentService).processPayment(anyLong(), any(), any(BigDecimal.class), any());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order order = orderService.createOrder(userId, userCouponId);

        // then
        assertNotNull(order);
        verify(paymentService).processPayment(eq(userId), any(), any(BigDecimal.class), eq(userCouponId));
    }

    @Test
    @DisplayName("주문 생성 - 장바구니 비어있음 실패")
    void create_order_empty_cart_fail() {
        // given
        Long userId = 100L;
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
        Long userId = 100L;
        User mockUser = User.builder()
                .userId(userId)
                .username("testUser")
                .point(new BigDecimal("100000"))
                .build();
        when(userRepository.findOne(userId)).thenReturn(mockUser);
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(productService.getProduct(1L)).thenReturn(product);
        doThrow(new NotEnoughStockException("재고 부족")).when(productService).deductStock(1L, 3);

        // when & then
        assertThrows(NotEnoughStockException.class, () ->
                orderService.createOrder(userId, null)
        );
        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentService, never()).processPayment(anyLong(), anyLong(), any(BigDecimal.class), any());
    }

    @Test
    @DisplayName("주문 생성 - 결제 실패 시 재고 복구")
    void create_order_payment_fail_restore_stock() {
        // given
        Long userId = 100L;
        User mockUser = User.builder()
                .userId(userId)
                .username("testUser")
                .point(new BigDecimal("100000"))
                .build();
        when(userRepository.findOne(userId)).thenReturn(mockUser);
        when(cartItemRepository.findByUserId(userId)).thenReturn(Arrays.asList(cartItem));
        when(productService.getProduct(1L)).thenReturn(product);
        doNothing().when(productService).deductStock(anyLong(), anyInt());
        doNothing().when(productService).updateOrderStatistics(anyLong(), anyInt());
        doThrow(new IllegalStateException("결제 실패")).when(paymentService)
                .processPayment(anyLong(), any(), any(BigDecimal.class), any());

        // when & then
        assertThrows(IllegalStateException.class, () ->
                orderService.createOrder(userId, null)
        );
        verify(productService).restoreStock(1L, 3);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 조회 - 성공")
    void get_order_success() {
        // given
        Long orderId = 1L;
        Order order = Order.builder()
                .orderId(orderId)
                .user(User.builder()
                        .userId(100L)
                        .username("testUser")
                        .point(new BigDecimal("100000"))
                        .build())
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
        Long orderId = 999L;
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
        Long userId = 100L;
        User testUser = User.builder()
                .userId(100L)
                .username("testUser")
                .point(new BigDecimal("100000"))
                .build();
        Order order1 = Order.builder().orderId(1L).user(testUser).build();
        Order order2 = Order.builder().orderId(2L).user(testUser).build();
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
        Long userId = 100L;
        when(orderRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // when
        var orders = orderService.getOrdersByUserId(userId);

        // then
        assertTrue(orders.isEmpty());
    }
}
