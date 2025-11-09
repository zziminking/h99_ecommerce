package h99.ecommerce.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItem {

    private int cartItemId;
    private int userId;
    private int productId;
    private int quantity;

    public CartItem(int cartItemId, int userId, int productId, int quantity) {
        validateQuantity(quantity);
        this.cartItemId = cartItemId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void updateQuantity(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    public void addQuantity(int additionalQuantity) {
        if (additionalQuantity <= 0) {
            throw new IllegalArgumentException("추가할 수량은 1 이상이어야 합니다");
        }
        this.quantity += additionalQuantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }
    }
}