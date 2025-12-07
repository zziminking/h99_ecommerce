package h99.ecommerce.dto;

import h99.ecommerce.domain.product.Product;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemDto {

    private Long cartItemId;
    private Long userId;
    private Product product;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
}
