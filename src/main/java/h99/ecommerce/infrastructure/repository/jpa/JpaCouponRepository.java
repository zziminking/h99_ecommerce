package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.repository.CouponRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCouponRepository implements CouponRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getCouponId() == null) {
            em.persist(coupon);
            return coupon;
        } else {
            return em.merge(coupon);
        }
    }

    @Override
    public Coupon findOne(Long couponId) {
        return em.find(Coupon.class, couponId);
    }

    @Override
    public List<Coupon> findAll() {
        return em.createQuery("SELECT c FROM Coupon c", Coupon.class).getResultList();
    }

    @Override
    public Coupon findByIdWithLock(Long couponId) {
        return em.createQuery(
                        "SELECT c FROM Coupon c WHERE c.couponId = :couponId",
                        Coupon.class)
                .setParameter("couponId", couponId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("javax.persistence.lock.timeout", 3000)
                .getSingleResult();
    }

}
