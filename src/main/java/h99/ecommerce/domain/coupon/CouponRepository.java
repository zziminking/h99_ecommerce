package h99.ecommerce.domain.coupon;

import h99.ecommerce.domain.coupon.Coupon;

import java.util.List;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Coupon findOne(Long couponId);

    List<Coupon> findAll();

    Coupon findByIdWithLock(Long couponId);
}
