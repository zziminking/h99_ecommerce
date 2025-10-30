package h99.ecommerce.controller;

import h99.ecommerce.dto.CartItemDto;
import h99.ecommerce.dto.CouponDto;
import h99.ecommerce.request.CartAddRequest;
import h99.ecommerce.request.CartUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "장바구니", description = "장바구니 관리 API")
public class CartMockController {

    // Mock 데이터
    private static final List<CartItemDto> MOCK_CART_ITEMS = createMockCartItems();

    // 장바구니 조회 (GET /api/cart/{userId})
    @GetMapping("/{userId}")
    @Operation(
            summary = "장바구니 조회",
            description = "특정 사용자의 장바구니 상품 목록을 조회합니다."
    )
    public ResponseEntity<List<CartItemDto>> getCart(
            @Parameter(description = "사용자 ID", example = "1", required = true)
            @PathVariable Integer userId
    ) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        // 해당 사용자의 장바구니 아이템 조회
        List<CartItemDto> cartItems = MOCK_CART_ITEMS.stream()
                .filter(item -> item.getUserId().equals(userId))
                .toList();

        return ResponseEntity.ok(cartItems);
    }

    // 장바구니 추가 (POST /api/cart)
    @PostMapping
    @Operation(
            summary = "장바구니 추가",
            description = "장바구니에 상품을 추가합니다"
    )
    public ResponseEntity<CartItemDto> addToCart(@RequestBody CartAddRequest request) {
        CartItemDto cartItem = CartItemDto.builder()
                .cartItemId(1100)
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .productName("테스트상품")
                .build();

        MOCK_CART_ITEMS.add(cartItem);

        return ResponseEntity.status(HttpStatus.CREATED).body(cartItem);
    }

    // 장바구니 수량 변경 (PATCH /api/cart/{cartItemId})
    @PatchMapping("/{cartItemId}")
    @Operation(
            summary = "장바구니 수량 변경",
            description = "장바구니 상품의 수량을 변경합니다."
    )
    public ResponseEntity<CartItemDto> updateCartItemQuantity(
            @Parameter(description = "장바구니 상품 ID", example = "1", required = true)
            @PathVariable Integer cartItemId,
            @RequestBody CartUpdateRequest request
    ) {
        // 장바구니 아이템 찾기
        CartItemDto cartItem = MOCK_CART_ITEMS.stream()
                .filter(item -> item.getCartItemId().equals(cartItemId))
                .findFirst()
                .orElse(null);

        if (cartItem == null) {
            return ResponseEntity.notFound().build();
        }

        // 수량 업데이트
        cartItem.setQuantity(request.getQuantity());
        cartItem.setTotalAmount(cartItem.getPrice().multiply(new BigDecimal(request.getQuantity())));

        return ResponseEntity.ok(cartItem);
    }

    // 장바구니 상품 삭제 (DELETE /api/cart/{cartItemId})
    @DeleteMapping("/{cartItemId}")
    @Operation(
            summary = "장바구니 상품 삭제",
            description = "장바구니에서 특정 상품을 삭제합니다."
    )
    public ResponseEntity<Void> removeFromCart(
            @Parameter(description = "장바구니 상품 ID", example = "1", required = true)
            @PathVariable Integer cartItemId
    ) {
        boolean removed = MOCK_CART_ITEMS.removeIf(item -> item.getCartItemId().equals(cartItemId));

        if (!removed) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    // Mock cartItem 데이터 생성
    private static List<CartItemDto> createMockCartItems() {
        List<CartItemDto> cartItems = new ArrayList<>();
        CartItemDto cartItem = CartItemDto.builder()
                .cartItemId(1)
                .userId(1)
                .productId(111)
                .quantity(20)
                .price(new BigDecimal("10000"))
                .productName("테스트상품2")
                .build();

        cartItems.add(cartItem);
        return cartItems;
    }

}
