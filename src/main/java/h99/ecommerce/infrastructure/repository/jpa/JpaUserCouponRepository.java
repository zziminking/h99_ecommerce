package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.coupon.UserCoupon;
import h99.ecommerce.domain.coupon.UserCouponRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserCouponRepository implements UserCouponRepository {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getUserCouponId() == null) {
            em.persist(userCoupon);
            return userCoupon;
        } else {
            return em.merge(userCoupon);
        }
    }

    @Override
    public UserCoupon findOne(Long userCouponId) {
        return em.find(UserCoupon.class, userCouponId);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return em.createQuery(
                        "SELECT uc FROM UserCoupon uc WHERE uc.user.userId = :userId",
                        UserCoupon.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        List<UserCoupon> results = em.createQuery(
                        "SELECT uc FROM UserCoupon uc WHERE uc.user.userId = :userId AND uc.coupon.couponId = :couponId",
                        UserCoupon.class)
                .setParameter("userId", userId)
                .setParameter("couponId", couponId)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

    }
}
