package h99.ecommerce.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "장바구니 수량 수정 요청")
public class CartUpdateRequest {
    
    @Schema(description = "수량", example = "3", required = true)
    private Integer quantity;
}
