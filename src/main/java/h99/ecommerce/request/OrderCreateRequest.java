package h99.ecommerce.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주문 생성 요청")
public class OrderCreateRequest {
    
    private Long userId;
    private List<OrderItemRequest> orderItems;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주문 상품 정보")
    public static class OrderItemRequest {

        @Schema(description = "상품 ID", example = "1", required = true)
        private Long productId;

        @Schema(description = "사용자 ID", example = "1", required = true)
        private Integer quantity;
    }
}
