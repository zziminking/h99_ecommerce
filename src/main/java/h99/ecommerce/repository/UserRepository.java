package h99.ecommerce.repository;

import h99.ecommerce.domain.User;

public interface UserRepository {

    int generateId();

    User save(User user);

    User findOne(int userId);
}
