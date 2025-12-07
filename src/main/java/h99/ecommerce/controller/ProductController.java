package h99.ecommerce.controller;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * 상품 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Product>> getProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * 상품 상세 조회 (조회수 증가)
     */
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(
            @PathVariable Long productId
    ) {
        if (productId == null || productId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Product product = productService.getProductWithViewCount(productId);
        return ResponseEntity.ok(product);
    }

    /**
     * 상품 재고 확인
     */
    @GetMapping("/{productId}/stock")
    public ResponseEntity<Boolean> checkStockAvailable(
            @PathVariable Long productId
    ) {
        if (productId == null || productId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        boolean available = productService.checkStockAvailable(productId);
        return ResponseEntity.ok(available);
    }

    /**
     * 상품 재고 충분 여부 확인
     */
    @GetMapping("/{productId}/stock/check")
    public ResponseEntity<Boolean> checkStockEnough(
            @PathVariable Long productId,
            @RequestParam Integer quantity
    ) {
        if (productId == null || productId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().build();
        }

        boolean enough = productService.checkStockEnough(productId, quantity);
        return ResponseEntity.ok(enough);
    }

    /**
     * 인기 상품 조회 (Redis Sorted Set 기반)
     */
    @GetMapping("/popular/{period}")
    public ResponseEntity<List<Product>> getPopularProducts(@PathVariable String period,
                                                            @RequestParam(defaultValue = "5") Integer limit) {
        if (!period.equals("3days") && !period.equals("7days")) {
            return ResponseEntity.badRequest().build();
        }

        if (limit == null || limit <= 0) {
            limit = 5;
        }

        List<Product> popularProducts = productService.getPopularProducts(period, limit);
        return ResponseEntity.ok(popularProducts);
    }

}
