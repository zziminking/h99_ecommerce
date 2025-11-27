package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.Point;
import h99.ecommerce.repository.PointRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaPointRepository implements PointRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public Point save(Point point) {
        if (point.getPointId() == null) {
            em.persist(point);
            return point;
        } else {
            return em.merge(point);
        }
    }

    @Override
    public Point findOne(Long pointId) {
        return em.find(Point.class, pointId);
    }

    @Override
    public List<Point> findByUserId(Long userId) {
        return em.createQuery("SELECT p FROM Point p WHERE p.userId = :userId", Point.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
