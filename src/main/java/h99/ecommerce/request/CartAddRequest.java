package h99.ecommerce.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "장바구니 추가 요청")
public class CartAddRequest {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    private Integer userId;
    
    @Schema(description = "상품 ID", example = "1", required = true)
    private Integer productId;
    
    @Schema(description = "수량", example = "2", required = true)
    private Integer quantity;
}
