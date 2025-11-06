package h99.ecommerce.service;

import h99.ecommerce.domain.Product;
import h99.ecommerce.exception.NotEnoughStockException;
import h99.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 단건 조회
     */
    public Product getProduct(int productId) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        // 조회수 증가
        product.increaseViewCount();
        productRepository.save(product);

        return product;
    }

    /**
     * 상품 전체 조회
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * 재고 존재 여부 확인
     */
    public boolean checkStockAvailable(int productId) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product.hasStock();
    }

    /**
     * 재고 충분 여부 확인
     */
    public boolean checkStockEnough(int productId, int quantity) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }
        return product.hasEnoughStock(quantity);
    }

    /**
     * 재고 차감
     */
    public void deductStock(int productId, int quantity) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        if (!product.hasStock()) {
            throw new NotEnoughStockException("품절된 상품입니다.");
        }

        if (!product.hasEnoughStock(quantity)) {
            throw new NotEnoughStockException("재고가 부족합니다. 현재 재고: " + product.getStock().getQuantity());
        }

        product.deductStock(quantity);
        productRepository.save(product);
    }

    /**
     * 재고 복구
     */
    public void restoreStock(int productId, int quantity) {
        Product product = productRepository.findOne(productId);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. productId: " + productId);
        }

        product.restoreStock(quantity);
        productRepository.save(product);
    }

}
