package h99.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "주문 상품 정보")
public class OrderItemDto {
    
    private Integer orderItemId;
    private Integer productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
}
