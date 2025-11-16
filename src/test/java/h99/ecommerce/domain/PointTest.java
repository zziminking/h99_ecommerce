package h99.ecommerce.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class PointTest {

    @Test
    @DisplayName("포인트 내역 생성 - 충전 (양수)")
    void create_point_charge_success() {
        // given & when
        Point point = new Point(1L, 0L, 100L, new BigDecimal("5000"), null);

        // then
        assertEquals(1, point.getPointId());
        assertEquals(0, point.getOrderId());
        assertEquals(100, point.getUserId());
        assertEquals(new BigDecimal("5000"), point.getAmount());
        assertNotNull(point.getCreatedAt());
    }

    @Test
    @DisplayName("포인트 내역 생성 - 사용 (음수)")
    void create_point_usage_success() {
        // given & when
        Point point = new Point(1L, 100L, 100L, new BigDecimal("-3000"), null);

        // then
        assertEquals(1, point.getPointId());
        assertEquals(100, point.getOrderId());
        assertEquals(100, point.getUserId());
        assertEquals(new BigDecimal("-3000"), point.getAmount());
        assertNotNull(point.getCreatedAt());
    }

    @Test
    @DisplayName("포인트 내역 생성 - 금액 null 실패")
    void create_point_with_null_amount_fail() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                new Point(1L, 100L, 100L, null, null)
        );
    }

    @Test
    @DisplayName("충전 내역 확인 - true (양수)")
    void is_charge_true() {
        // given
        Point point = new Point(1L, 0L, 100L, new BigDecimal("5000"), null);

        // when
        boolean result = point.isCharge();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("충전 내역 확인 - false (음수)")
    void is_charge_false() {
        // given
        Point point = new Point(1L, 100L, 100L, new BigDecimal("-3000"), null);

        // when
        boolean result = point.isCharge();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("충전 내역 확인 - false (0)")
    void is_charge_false_zero() {
        // given
        Point point = new Point(1L, 0L, 100L, BigDecimal.ZERO, null);

        // when
        boolean result = point.isCharge();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("사용 내역 확인 - true (음수)")
    void is_usage_true() {
        // given
        Point point = new Point(1L, 100L, 100L, new BigDecimal("-3000"), null);

        // when
        boolean result = point.isUsage();

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("사용 내역 확인 - false (양수)")
    void is_usage_false() {
        // given
        Point point = new Point(1L, 0L, 100L, new BigDecimal("5000"), null);

        // when
        boolean result = point.isUsage();

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("사용 내역 확인 - false (0)")
    void is_usage_false_zero() {
        // given
        Point point = new Point(1L, 0L, 100L, BigDecimal.ZERO, null);

        // when
        boolean result = point.isUsage();

        // then
        assertFalse(result);
    }
}
