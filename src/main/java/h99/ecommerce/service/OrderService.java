package h99.ecommerce.service;

import h99.ecommerce.domain.cartitem.CartItem;
import h99.ecommerce.domain.order.Order;
import h99.ecommerce.domain.order.OrderItem;
import h99.ecommerce.domain.order.OrderStatus;
import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.user.User;
import h99.ecommerce.event.OrderCompletedEvent;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.domain.cartitem.CartItemRepository;
import h99.ecommerce.domain.order.OrderRepository;
import h99.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 장바구니 기반 주문 생성
     */
    @Transactional
    public Order createOrder(Long userId, Long userCouponId) {
        // 1. 장바구니 조회
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("장바구니가 비어있습니다.");
        }

        // User 조회
        User user = userRepository.findOne(userId);

        // 2. 주문 생성
        Order order = Order.builder()
                .user(user)
                .totalQuantity(0)
                .totalPrice(BigDecimal.ZERO)
                .orderItems(new ArrayList<>())
                .build();

        try {
            // 3. 장바구니 아이템 -> 주문 아이템 변환 및 재고 차감
            for (CartItem cartItem : cartItems) {
                Product product = productService.getProduct(cartItem.getProductId());

                // 재고 차감 (재고 부족 시 예외 발생)
                productService.deductStock(cartItem.getProductId(), cartItem.getQuantity());

                // 주문 아이템 생성
                OrderItem orderItem = createOrderItem(order, cartItem, product);
                order.addOrderItem(orderItem);

                // 주문 수량 통계 업데이트
                productService.updateOrderStatistics(cartItem.getProductId(), cartItem.getQuantity());
            }

            // 4. 주문 총액 및 총 수량 계산
            order.calculateTotalPrice();
            order.calculateTotalQuantity();

            // 5. 결제 처리
            BigDecimal finalAmount = order.getTotalPrice();
            paymentService.processPayment(userId, order.getOrderId(), finalAmount, userCouponId);

            // 6. 주문 저장
            orderRepository.save(order);

            // 7. 장바구니 비우기
            clearCart(userId);

            // 8. 주문 완료 이벤트 발행 (트랜잭션 커밋 후 외부 시스템 연동)
            eventPublisher.publishEvent(OrderCompletedEvent.from(order, userId));
            log.info("주문 완료 이벤트 발행 - orderId: {}, userId: {}", order.getOrderId(), userId);

            return order;

        } catch (NotEnoughStockException | IllegalStateException e) {
            restoreStock(order);
            throw e;
        } catch (Exception e) {
            restoreStock(order);
            throw new RuntimeException("주문 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 주문 아이템 생성
     */
    private OrderItem createOrderItem(Order orderId, CartItem cartItem, Product product) {
        BigDecimal itemPrice = product.getPrice();
        int quantity = cartItem.getQuantity();
        BigDecimal totalAmount = itemPrice.multiply(new BigDecimal(quantity));

        OrderItem orderItem = OrderItem.builder()
                .order(orderId)
                .product(product)
                .quantity(quantity)
                .status(OrderStatus.PENDING)
                .price(itemPrice)
                .totalAmount(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(totalAmount)
                .build();

        orderItem.calculateTotalAmount();
        orderItem.calculateFinalAmount();

        return orderItem;
    }

    /**
     * 주문에 대한 재고 복구 (보상 트랜잭션)
     */
    private void restoreStock(Order order) {
        if (order == null || order.getOrderItems() == null) {
            return;
        }

        for (OrderItem orderItem : order.getOrderItems()) {
            try {
                productService.restoreStock(orderItem.getProductId(), orderItem.getQuantity());
            } catch (Exception e) {
                log.error("재고 복구 실패 - productId: {}, quantity: {}, error: {}",
                        orderItem.getProductId(),
                        orderItem.getQuantity(),
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * 장바구니 비우기
     */
    private void clearCart(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        for (CartItem cartItem : cartItems) {
            cartItemRepository.delete(cartItem.getCartItemId());
        }
    }

    /**
     * 주문 조회
     */
    public Order getOrder(Long orderId) {
        Order order = orderRepository.findOne(orderId);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. orderId: " + orderId);
        }
        return order;
    }

    /**
     * 사용자별 주문 목록 조회
     */
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}
