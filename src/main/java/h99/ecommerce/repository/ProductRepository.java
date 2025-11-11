package h99.ecommerce.repository;

import h99.ecommerce.domain.Product;
import java.util.List;

public interface ProductRepository {

    Product save(Product product);

    Product findOne(Long productId);

    List<Product> findAll();

    void delete(Long productId);
}
