package h99.ecommerce.repository;

import h99.ecommerce.domain.Product;
import java.util.List;

public interface ProductRepository {

    int generateId();

    Product save(Product product);

    Product findOne(int productId);

    List<Product> findAll();

    void delete(int productId);
}
