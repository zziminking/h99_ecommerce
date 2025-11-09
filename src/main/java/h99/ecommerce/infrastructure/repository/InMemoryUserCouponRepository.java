package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.UserCoupon;
import h99.ecommerce.repository.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<Integer, UserCoupon> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public int generateId() {
        return idGenerator.getAndIncrement();
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new IllegalArgumentException("UserCoupon cannot be null");
        }
        store.put(userCoupon.getUserCouponId(), userCoupon);
        return userCoupon;
    }

    @Override
    public UserCoupon findOne(int userCouponId) {
        return store.get(userCouponId);
    }

    @Override
    public List<UserCoupon> findByUserId(int userId) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUserId() == userId)
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(int userId, int couponId) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUserId() == userId 
                        && userCoupon.getCouponId() == couponId)
                .findFirst();
    }
}
