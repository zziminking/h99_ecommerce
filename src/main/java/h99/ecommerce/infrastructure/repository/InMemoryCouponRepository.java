package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Coupon;
import h99.ecommerce.repository.CouponRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemoryCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon == null) {
            throw new IllegalArgumentException("Coupon cannot be null");
        }
        store.put(coupon.getCouponId(), coupon);
        return coupon;
    }

    @Override
    public Coupon findOne(Long couponId) {
        return store.get(couponId);
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(store.values());
    }
}
