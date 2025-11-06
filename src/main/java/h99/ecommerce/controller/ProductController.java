package h99.ecommerce.controller;

import h99.ecommerce.domain.Product;
import h99.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 상품 상세 조회
     */
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(
            @PathVariable Integer productId
    ) {
        if (productId == null || productId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Product product = productService.getProduct(productId);
        return ResponseEntity.ok(product);
    }

    /**
     * 상품 재고 확인
     */
    @GetMapping("/{productId}/stock")
    public ResponseEntity<Boolean> checkStockAvailable(
            @PathVariable Integer productId
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
            @PathVariable Integer productId,
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
}
