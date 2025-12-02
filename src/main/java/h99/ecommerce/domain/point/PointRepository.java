package h99.ecommerce.domain.point;

import h99.ecommerce.domain.point.Point;

import java.util.List;

public interface PointRepository {

    Point save(Point point);

    Point findOne(Long pointId);

    List<Point> findByUserId(Long userId);
}
