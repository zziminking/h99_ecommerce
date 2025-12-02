package h99.ecommerce.service;

import h99.ecommerce.domain.cartitem.CartItem;
import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.cartitem.CartItemRepository;
import h99.ecommerce.domain.product.ProductRepository;
import h99.ecommerce.dto.CartItemDto;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.request.CartAddRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public List<CartItemDto> getCart(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        List<CartItemDto> cartItemDtoList = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findOne(cartItem.getProductId());

            CartItemDto cartItemDto = CartItemDto.builder()
                    .cartItemId(cartItem.getCartItemId())
                    .userId(cartItem.getUserId())
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .totalAmount(product.getPrice().multiply(new java.math.BigDecimal(cartItem.getQuantity())))
                    .build();

            cartItemDtoList.add(cartItemDto);
        }

        return cartItemDtoList;
    }

    @Transactional
    public void addCartItem(CartAddRequest request) {
        Product product = productRepository.findOne(request.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }

        if (!product.hasStock()) {
            throw new NotEnoughStockException("품절된 상품입니다.");
        }

        // 기존 장바구니 아이템 확인
        Optional<CartItem> existingCartItem = cartItemRepository.findByUserIdAndProductId(
                request.getUserId(),
                request.getProductId()
        );

        if (existingCartItem.isPresent()) {
            // 기존 아이템이 있으면 수량 증가
            CartItem cartItem = existingCartItem.get();
            int newQuantity = cartItem.getQuantity() + request.getQuantity();

            if (!product.hasEnoughStock(newQuantity)) {
                throw new NotEnoughStockException("재고가 부족합니다. 현재 재고: " + product.getStock().getQuantity());
            }

            cartItemRepository.updateQuantity(cartItem.getCartItemId(), newQuantity);
        } else {
            // 새 아이템 추가
            if (!product.hasEnoughStock(request.getQuantity())) {
                throw new NotEnoughStockException("재고가 부족합니다. 현재 재고: " + product.getStock().getQuantity());
            }

            CartItem newCartItem = CartItem.builder()
                    .userId(request.getUserId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(newCartItem);
        }
    }

    public void updateCartItemQuantity(Long cartItemId, int newQuantity) {
        CartItem cartItem = cartItemRepository.findOne(cartItemId);
        if (cartItem == null) {
            throw new IllegalArgumentException("장바구니 아이템을 찾을 수 없습니다.");
        }

        if (newQuantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        Product product = productRepository.findOne(cartItem.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }

        if (!product.hasEnoughStock(newQuantity)) {
            throw new NotEnoughStockException("재고가 부족합니다. 현재 재고: " + product.getStock().getQuantity());
        }

        cartItemRepository.updateQuantity(cartItemId, newQuantity);
    }

    public void removeCartItem(Long cartItemId) {
        CartItem cartItem = cartItemRepository.findOne(cartItemId);
        if (cartItem == null) {
            throw new IllegalArgumentException("장바구니 아이템을 찾을 수 없습니다.");
        }

        cartItemRepository.delete(cartItemId);
    }

    public java.math.BigDecimal getCartTotal(Long userId) {
        List<CartItemDto> cart = getCart(userId);
        return cart.stream()
                .map(CartItemDto::getTotalAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}