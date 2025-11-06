package h99.ecommerce.repository;

import h99.ecommerce.domain.UserCoupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    int generateId();

    UserCoupon save(UserCoupon userCoupon);

    UserCoupon findOne(int userCouponId);

    List<UserCoupon> findByUserId(int userId);

    Optional<UserCoupon> findByUserIdAndCouponId(int userId, int couponId);
}
