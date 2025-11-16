package h99.ecommerce.repository;

import h99.ecommerce.domain.UserCoupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    UserCoupon findOne(Long userCouponId);

    List<UserCoupon> findByUserId(Long userId);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
}
