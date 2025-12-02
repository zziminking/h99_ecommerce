package h99.ecommerce.domain.coupon;

import h99.ecommerce.domain.coupon.UserCoupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    UserCoupon findOne(Long userCouponId);

    List<UserCoupon> findByUserId(Long userId);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
}
