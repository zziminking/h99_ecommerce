package h99.ecommerce.controller;

import h99.ecommerce.dto.ProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "상품", description = "상품 관리 API")
public class ProductMockController {

    // Mock 데이터
    private static final List<ProductDto> MOCK_PRODUCTS = createMockProducts();

    // 상품 목록 조회 API (GET / api/products)
    @GetMapping
    @Operation(summary = "상품 목록 조회", description = "전체 상품 목록을 조회합니다")
    public ResponseEntity<List<ProductDto>> getProducts() {
        return ResponseEntity.ok(MOCK_PRODUCTS);
    }

    // 상품 상세 조회 API (GET / api/products/{productId})
    @GetMapping("/{productId}")
    @Operation(summary = "상품 상세 조회", description = "product_id로 특정 상품을 조회합니다.")
    public ResponseEntity<ProductDto> getProduct(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Integer productId
    ) {
        ProductDto productDto = MOCK_PRODUCTS.stream()
                .filter(p -> p.getProductId().equals(productId))
                .findFirst()
                .orElse(null);

        if (productDto == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(productDto);
    }

    // Mock 데이터 생성
    private static List<ProductDto> createMockProducts() {
        List<ProductDto> products = new ArrayList<>();

        products.add(new ProductDto(1, "자바", 10000, 10));
        products.add(new ProductDto(2, "파이썬", 20000, 10));
        products.add(new ProductDto(3, "C", 30000, 10));
        return products;
    }
}
