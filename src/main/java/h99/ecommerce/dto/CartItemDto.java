package h99.ecommerce.dto;

import h99.ecommerce.domain.Product;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemDto {
    
    private Integer cartItemId;
    private Integer userId;
    private Product product;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
}
