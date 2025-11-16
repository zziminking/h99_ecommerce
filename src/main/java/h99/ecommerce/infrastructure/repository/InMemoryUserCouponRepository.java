package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.repository.UserCouponRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new IllegalArgumentException("UserCoupon cannot be null");
        }

        if (userCoupon.getUserCouponId() == null) {
            // Auto-generate ID for new UserCoupon
            Long newId = (long) idGenerator.getAndIncrement();
            UserCoupon newUserCoupon = UserCoupon.builder()
                    .userCouponId(newId)
                    .user(userCoupon.getUser())
                    .coupon(userCoupon.getCoupon())
                    .isUsed(userCoupon.isUsed())
                    .usedAt(userCoupon.getUsedAt())
                    .build();
            store.put(newId, newUserCoupon);
            return newUserCoupon;
        }

        store.put(userCoupon.getUserCouponId(), userCoupon);
        return userCoupon;
    }

    @Override
    public UserCoupon findOne(Long userCouponId) {
        return store.get(userCouponId);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUserId().equals(userId))
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUserId().equals(userId)
                        && userCoupon.getCouponId().equals(couponId))
                .findFirst();
    }
}
