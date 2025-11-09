package h99.ecommerce.infrastructure.repository;

import h99.ecommerce.domain.Point;
import h99.ecommerce.repository.PointRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryPointRepository implements PointRepository {

    private final Map<Integer, Point> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public int generateId() {
        return idGenerator.getAndIncrement();
    }

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
        return store.get(pointId);
    }

    @Override
    public List<Point> findByUserId(int userId) {
        return store.values().stream()
                .filter(point -> point.getUserId() == userId)
                .toList();
    }
}
