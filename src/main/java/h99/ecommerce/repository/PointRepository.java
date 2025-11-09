package h99.ecommerce.repository;

import h99.ecommerce.domain.Point;

import java.util.List;

public interface PointRepository {

    int generateId();

    Point save(Point point);

    Point findOne(int pointId);

    List<Point> findByUserId(int userId);
}
