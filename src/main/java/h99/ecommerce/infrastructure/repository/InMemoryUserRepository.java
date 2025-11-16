package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.User;
import h99.ecommerce.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        store.put(user.getUserId(), user);
        return user;
    }

    @Override
    public User findOne(Long userId) {
        return store.get(userId);
    }
}
