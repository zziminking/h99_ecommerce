package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Product;
import h99.ecommerce.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        store.put(product.getProductId(), product);
        return product;
    }

    @Override
    public Product findOne(Long productId) {
        return store.get(productId);
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(Long productId) {
        store.remove(productId);
    }

    public void clear() {
        store.clear();
    }
}
