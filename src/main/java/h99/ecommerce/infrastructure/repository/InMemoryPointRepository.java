package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Point;
import h99.ecommerce.repository.PointRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemoryPointRepository implements PointRepository {

    private final Map<Long, Point> store = new ConcurrentHashMap<>();

    @Override
    public Point save(Point point) {
        if (point == null) {
            throw new IllegalArgumentException("Point cannot be null");
        }
        store.put(point.getPointId(), point);
        return point;
    }

    @Override
    public Point findOne(int pointId) {
        return store.get((long) pointId);
    }

    @Override
    public List<Point> findByUserId(int userId) {
        return store.values().stream()
                .filter(point -> point.getUserId() == userId)
                .toList();
    }
}
