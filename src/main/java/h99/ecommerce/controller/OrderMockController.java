package h99.ecommerce.controller;

import h99.ecommerce.dto.OrderDto;
import h99.ecommerce.dto.OrderItemDto;
import h99.ecommerce.request.OrderCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "주문", description = "주문 생성 API")
public class OrderMockController {

    // 주문 생성 (POST /api/orders)
    @PostMapping
    @Operation(
            summary = "주문 생성",
            description = "새로운 주문을 생성합니다. 주문 상품 목록과 사용자 ID가 필요합니다."
    )
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderCreateRequest request) {
        // Mock 주문 상품 생성
        List<OrderItemDto> orderItems = new ArrayList<>();

        for (OrderCreateRequest.OrderItemRequest item : request.getOrderItems()) {
            OrderItemDto orderItem = OrderItemDto.builder()
                    .orderItemId(1)
                    .productId(item.getProductId())
                    .productName("Mock 상품")
                    .quantity(item.getQuantity())
                    .price(new BigDecimal("10000"))
                    .status("PENDING")
                    .totalAmount(new BigDecimal("10000"))
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(new BigDecimal("10000"))
                    .build();
            orderItems.add(orderItem);
        }

        // Mock 주문 생성
        OrderDto orderDto = OrderDto.builder()
                .orderId(1)
                .userId(request.getUserId())
                .totalAmount(new BigDecimal("10000"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("10000"))
                .orderItems(orderItems)
                .createdAt(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(orderDto);
    }
}
