package h99.ecommerce.domain.product;

import h99.ecommerce.domain.product.Product;
import java.util.List;

public interface ProductRepository {

    Product save(Product product);

    Product findOne(Long productId);

    List<Product> findAll();

    void delete(Long productId);

    int deductStockConditional(Long productId, int quantity);
}
