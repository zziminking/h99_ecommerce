package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.product.Product;
import h99.ecommerce.domain.product.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
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

    @Override
    public int deductStockConditional(Long productId, int quantity) {
        return em.createQuery(
                        "UPDATE Product a "
                                + "SET a.stock.quantity = a.stock.quantity - :quantity "
                                + "WHERE a.productId = :productId "
                                + "AND a.stock.quantity >= :quantity")
                .setParameter("productId", productId)
                .setParameter("quantity", quantity)
                .executeUpdate();
    }
}
