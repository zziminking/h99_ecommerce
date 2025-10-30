package h99.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "상품 정보")
public class ProductDto {

    private Integer productId;
    private String name;
    private int price;
    private Integer stock;
}
