package h99.ecommerce.controller;

import h99.ecommerce.domain.order.Order;
import h99.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 생성 (장바구니 기반)
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestParam Long userId,
            @RequestParam(required = false) Long userCouponId
    ) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (userCouponId != null && userCouponId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Order order = orderService.createOrder(userId, userCouponId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * 주문 상세 조회
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        if (orderId == null || orderId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    /**
     * 사용자별 주문 목록 조회
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        List<Order> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }
}
