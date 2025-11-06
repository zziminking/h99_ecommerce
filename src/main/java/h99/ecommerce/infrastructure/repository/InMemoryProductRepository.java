package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Product;
import h99.ecommerce.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Integer, Product> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public int generateId() {
        return idGenerator.getAndIncrement();
    }

    @Override
    public Product save(Product product) {
        store.put(product.getProductId(), product);
        return product;
    }

    @Override
    public Product findOne(int productId) {
        return store.get(productId);
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(int productId) {
        store.remove(productId);
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}
