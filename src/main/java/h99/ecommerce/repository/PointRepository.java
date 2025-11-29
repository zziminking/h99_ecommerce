package h99.ecommerce.repository;

import h99.ecommerce.domain.Point;

import java.util.List;

public interface PointRepository {

    Point save(Point point);

    Point findOne(Long pointId);

    List<Point> findByUserId(Long userId);
}
