package h99.ecommerce.controller;

import h99.ecommerce.dto.CartItemDto;
import h99.ecommerce.request.CartAddRequest;
import h99.ecommerce.request.CartUpdateRequest;
import h99.ecommerce.service.CartItemService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartItemController {

    private final CartItemService cartItemService;

    /**
     * 장바구니 조회
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<CartItemDto>> getCart(
            @PathVariable Integer userId
    ) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        List<CartItemDto> cartItems = cartItemService.getCart(userId);
        return ResponseEntity.ok(cartItems);
    }

    /**
     * 장바구니 총액 조회
     */
    @GetMapping("/{userId}/total")
    public ResponseEntity<BigDecimal> getCartTotal(
            @PathVariable Integer userId
    ) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        BigDecimal total = cartItemService.getCartTotal(userId);
        return ResponseEntity.ok(total);
    }

    /**
     * 장바구니 추가
     */
    @PostMapping
    public ResponseEntity<Void> addToCart(@RequestBody CartAddRequest request) {
        if (request.getUserId() == null || request.getUserId() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getProductId() == null || request.getProductId() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        cartItemService.addCartItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 장바구니 수량 변경
     */
    @PatchMapping("/{cartItemId}")
    public ResponseEntity<Void> updateCartItemQuantity(
            @PathVariable Integer cartItemId,
            @RequestBody CartUpdateRequest request
    ) {
        if (cartItemId == null || cartItemId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        cartItemService.updateCartItemQuantity(cartItemId, request.getQuantity());
        return ResponseEntity.ok().build();
    }

    /**
     * 장바구니 상품 삭제
     */
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable Integer cartItemId
    ) {
        if (cartItemId == null || cartItemId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        cartItemService.removeCartItem(cartItemId);
        return ResponseEntity.noContent().build();
    }
}
