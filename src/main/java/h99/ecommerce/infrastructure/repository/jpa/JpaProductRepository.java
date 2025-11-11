package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.Product;
import h99.ecommerce.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@RequiredArgsConstructor
public class JpaProductRepository implements ProductRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public Product save(Product product) {
        if (product.getProductId() == null) {
            em.persist(product);
            return product;
        } else {
            return em.merge(product);
        }
    }

    @Override
    public Product findOne(Long productId) {
        return em.find(Product.class, productId);
    }

    @Override
    public List<Product> findAll() {
        return em.createQuery("SELECT p FROM Product p", Product.class).getResultList();
    }

    @Override
    public void delete(Long productId) {
        em.remove(em.find(Product.class, productId));
    }
}
