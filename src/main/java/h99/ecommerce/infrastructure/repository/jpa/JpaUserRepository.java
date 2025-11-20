package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.User;
import h99.ecommerce.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {

    @PersistenceContext
    private final EntityManager em;


    @Override
    public User save(User user) {
        if (user.getUserId() == null) {
            em.persist(user);
            return user;
        } else {
            return em.merge(user);
        }
    }

    @Override
    public User findOne(Long userId) {
        return em.find(User.class, userId);
    }
}
