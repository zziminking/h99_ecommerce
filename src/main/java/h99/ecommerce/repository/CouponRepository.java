package h99.ecommerce.repository;

import h99.ecommerce.domain.Coupon;

import java.util.List;

public interface CouponRepository {

    int generateId();

    Coupon save(Coupon coupon);

    Coupon findOne(int couponId);

    List<Coupon> findAll();
}
