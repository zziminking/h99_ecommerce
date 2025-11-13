package h99.ecommerce.infrastructure.repository.jpa;

import h99.ecommerce.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JpaUserRepository 통합 테스트")
class JpaUserRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaUserRepository userRepository;

    @Test
    @DisplayName("사용자 ID로 조회 - 성공")
    void find_user_by_id_should_return_user() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(new BigDecimal("5000"))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        // when
        User foundUser = userRepository.findOne(savedUser.getUserId());

        // then
        assertThat(foundUser.getUserId()).isEqualTo(savedUser.getUserId());
    }

    @Test
    @DisplayName("사용자 ID로 조회 - 존재하지 않으면 null 반환")
    void find_user_by_non_existing_id_should_return_null() {
        // when
        User foundUser = userRepository.findOne(999L);

        // then
        assertNull(foundUser);
    }

    @Test
    @DisplayName("사용자 업데이트 - merge 동작 확인")
    void update_user_should_merge_successfully() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(new BigDecimal("10000"))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        // when - 기존 엔티티를 수정하여 다시 저장
        User userToUpdate = User.builder()
                .userId(savedUser.getUserId())
                .username("updatedUser")
                .point(new BigDecimal("20000"))
                .build();
        User updatedUser = userRepository.save(userToUpdate);
        flushAndClear();

        // then
        User foundUser = userRepository.findOne(savedUser.getUserId());
        assertThat(foundUser.getUsername()).isEqualTo("updatedUser");
        assertThat(foundUser.getPoint()).isEqualByComparingTo(new BigDecimal("20000"));
    }

    @Test
    @DisplayName("사용자 포인트 충전 - Dirty Checking 확인")
    void charge_point_should_update_via_dirty_checking() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(new BigDecimal("10000"))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        // when - 영속성 컨텍스트에서 엔티티를 조회하고 도메인 메서드 호출
        User managedUser = userRepository.findOne(savedUser.getUserId());
        managedUser.chargePoint(new BigDecimal("5000"));
        flushAndClear();  // 변경 감지로 UPDATE 쿼리 실행

        // then
        User foundUser = userRepository.findOne(savedUser.getUserId());
        assertThat(foundUser.getPoint()).isEqualByComparingTo(new BigDecimal("15000"));
    }

    @Test
    @DisplayName("사용자 포인트 차감 - Dirty Checking 확인")
    void deduct_point_should_update_via_dirty_checking() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(new BigDecimal("10000"))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        // when
        User managedUser = userRepository.findOne(savedUser.getUserId());
        managedUser.deductPoint(new BigDecimal("3000"));
        flushAndClear();

        // then
        User foundUser = userRepository.findOne(savedUser.getUserId());
        assertThat(foundUser.getPoint()).isEqualByComparingTo(new BigDecimal("7000"));
    }

    @Test
    @DisplayName("사용자 포인트 차감 - 잔액 부족 시 예외 발생")
    void deduct_point_with_insufficient_balance_should_throw_exception() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(new BigDecimal("1000"))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        // when & then
        User managedUser = userRepository.findOne(savedUser.getUserId());
        assertThrows(IllegalStateException.class, () ->
                managedUser.deductPoint(new BigDecimal("5000"))
        );
    }

    @Test
    @DisplayName("트랜잭션 롤백 확인 - 예외 발생 시 저장 안됨")
    void transaction_rollback_on_exception() {
        // given
        User user = User.builder()
                .username("testUser")
                .point(new BigDecimal("10000"))
                .build();
        User savedUser = userRepository.save(user);
        flushAndClear();

        // when - 예외 발생으로 트랜잭션 롤백 시뮬레이션
        try {
            User managedUser = userRepository.findOne(savedUser.getUserId());
            managedUser.chargePoint(new BigDecimal("5000"));
            // 강제로 예외 발생
            throw new RuntimeException("의도적 예외");
        } catch (RuntimeException e) {
            // 트랜잭션 롤백
            entityManager.clear();
        }

        // then - 포인트 충전이 롤백되어 원래 값 유지
        User foundUser = userRepository.findOne(savedUser.getUserId());
        assertThat(foundUser.getPoint()).isEqualByComparingTo(new BigDecimal("10000"));
    }
}
