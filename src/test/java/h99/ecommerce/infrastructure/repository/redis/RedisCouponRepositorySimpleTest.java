package h99.ecommerce.infrastructure.repository.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.dto.CouponIssueResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis Coupon Repository 단위 테스트
 * 실제 Redis 연결 필요
 */
class RedisCouponRepositorySimpleTest {
    
    private RedisCouponRepository repository;
    private RedisTemplate<String, String> redisTemplate;
    
    @BeforeEach
    void setUp() {
        // Redis 연결
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        
        // RedisTemplate 설정
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
        
        // ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Repository 생성
        repository = new RedisCouponRepository(redisTemplate, objectMapper);
        repository.init();
        
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @Test
    @DisplayName("쿠폰 Redis 초기화 - 간단 테스트")
    void initializeCoupon() {
        // given
        Coupon coupon = Coupon.builder()
            .couponId(1L)
            .name("테스트 쿠폰")
            .discountType(DiscountType.FIXED)
            .discountValue(BigDecimal.valueOf(1000))
            .maxIssueCount(100)
            .issuedCount(0)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(7))
            .status(CouponStatus.ACTIVE)
            .build();
        
        // when
        repository.initializeCoupon(coupon);
        
        // then
        Integer stock = repository.getCouponStock(1L).orElse(0);
        assertThat(stock).isEqualTo(100);
    }
    
    @Test
    @DisplayName("쿠폰 발급 성공 - 간단 테스트")
    void issueCouponSuccess() {
        // given
        Coupon coupon = Coupon.builder()
            .couponId(1L)
            .name("테스트 쿠폰")
            .discountType(DiscountType.FIXED)
            .discountValue(BigDecimal.valueOf(1000))
            .maxIssueCount(100)
            .issuedCount(0)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(7))
            .status(CouponStatus.ACTIVE)
            .build();
        
        repository.initializeCoupon(coupon);
        
        // when
        CouponIssueResult result = repository.issueCouponAtomic(1L, 100L);
        
        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRank()).isEqualTo(1);
        assertThat(result.getRemainingStock()).isEqualTo(99);
    }
}
