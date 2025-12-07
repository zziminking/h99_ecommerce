package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;

import h99.ecommerce.domain.point.Point;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaPointRepository 통합 테스트")
class JpaPointRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaPointRepository pointRepository;

    @Test
    @DisplayName("포인트 저장")
    void save_point() {
        // given
        Point point = Point.builder()
                .orderId(1L)
                .userId(1L)
                .amount(BigDecimal.valueOf(1000))
                .build();

        // when
        Point saved = pointRepository.save(point);
        flushAndClear();

        // then
        assertThat(point.getPointId()).isEqualTo(saved.getPointId());
    }

    @Test
    @DisplayName("포인트 조회")
    void findOne_by_id() {
        // given
        Point point = Point.builder()
                .orderId(2L)
                .userId(2L)
                .amount(BigDecimal.valueOf(5000))
                .build();
        Point saved = pointRepository.save(point);
        flushAndClear();

        // when
        Point found = pointRepository.findOne(saved.getPointId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getOrderId()).isEqualTo(2L);
        assertThat(found.getUserId()).isEqualTo(2L);
        assertThat(found.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("사용자 ID로 포인트 목록 조회")
    void find_points_by_user_id() {
        // given
        Long userId = 3L;

        Point point1 = Point.builder()
                .orderId(10L)
                .userId(userId)
                .amount(BigDecimal.valueOf(1000))
                .build();
        Point point2 = Point.builder()
                .orderId(11L)
                .userId(userId)
                .amount(BigDecimal.valueOf(-500))
                .build();
        Point point3 = Point.builder()
                .orderId(12L)
                .userId(userId)
                .amount(BigDecimal.valueOf(2000))
                .build();

        pointRepository.save(point1);
        pointRepository.save(point2);
        pointRepository.save(point3);
        flushAndClear();

        // when
        List<Point> points = pointRepository.findByUserId(userId);

        // then
        assertThat(points).hasSize(3);
        assertThat(points).extracting("amount")
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(-500),
                        BigDecimal.valueOf(2000)
                );
    }

    @Test
    @DisplayName("충전 포인트 저장 및 조회")
    void save_and_find_charge_point() {
        // given
        Point chargePoint = Point.builder()
                .orderId(null)
                .userId(4L)
                .amount(BigDecimal.valueOf(10000))
                .build();
        Point saved = pointRepository.save(chargePoint);
        flushAndClear();

        // when
        Point found = pointRepository.findOne(saved.getPointId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.isCharge()).isTrue();
        assertThat(found.isUsage()).isFalse();
    }

    @Test
    @DisplayName("사용 포인트 저장 및 조회")
    void save_and_find_usage_point() {
        // given
        Point usagePoint = Point.builder()
                .orderId(20L)
                .userId(5L)
                .amount(BigDecimal.valueOf(-3000))
                .build();
        Point saved = pointRepository.save(usagePoint);
        flushAndClear();

        // when
        Point found = pointRepository.findOne(saved.getPointId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.isCharge()).isFalse();
        assertThat(found.isUsage()).isTrue();
    }
}
