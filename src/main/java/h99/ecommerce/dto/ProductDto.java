package h99.ecommerce.dto;

import h99.ecommerce.domain.vo.Stock;
import java.math.BigDecimal;
import lombok.Builder;

@Builder
public class ProductDto {

    private Integer productId;
    private String name;
    private String description;
    private BigDecimal price;
    private Stock stock;
}
