package h99.ecommerce.domain.user;

import h99.ecommerce.domain.user.User;

public interface UserRepository {

    User save(User user);

    User findOne(Long userId);
}
