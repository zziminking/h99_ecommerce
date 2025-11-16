package h99.ecommerce.repository;

import h99.ecommerce.domain.User;

public interface UserRepository {

    User save(User user);

    User findOne(Long userId);
}
