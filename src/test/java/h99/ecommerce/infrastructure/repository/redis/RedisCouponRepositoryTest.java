package h99.ecommerce.infrastructure.repository.redis;

import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.domain.coupon.CouponStatus;
import h99.ecommerce.domain.coupon.DiscountType;
import h99.ecommerce.dto.CouponIssueResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class RedisCouponRepositoryTest {
    
    @Autowired
    private RedisCouponRepository couponRedisRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @BeforeEach
    void setUp() {
        // Redis 데이터 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @AfterEach
    void tearDown() {
        // 테스트 후 Redis 정리
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @Test
    @DisplayName("쿠폰 Redis 초기화")
    void initializeCoupon() {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        
        // when
        couponRedisRepository.initializeCoupon(coupon);
        
        // then
        Integer stock = couponRedisRepository.getCouponStock(1L).orElse(0);
        assertThat(stock).isEqualTo(100);
    }
    
    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCouponSuccess() {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);
        
        // when
        CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, 100L);
        
        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRank()).isEqualTo(1);  // 첫 발급
        assertThat(result.getRemainingStock()).isEqualTo(99);
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getCouponId()).isEqualTo(1L);
    }
    
    @Test
    @DisplayName("중복 발급 방지")
    void preventDuplicateIssue() {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);
        couponRedisRepository.issueCouponAtomic(1L, 100L);  // 첫 발급
        
        // when
        CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, 100L);  // 중복 시도
        
        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ALREADY_ISSUED");
        assertThat(result.getErrorMessage()).contains("이미 발급받은 쿠폰");
    }
    
    @Test
    @DisplayName("재고 소진 시 발급 실패")
    void soldOutPrevention() {
        // given
        Coupon coupon = createTestCoupon(1L, 1);  // 재고 1개
        couponRedisRepository.initializeCoupon(coupon);
        couponRedisRepository.issueCouponAtomic(1L, 100L);  // 첫 발급 (재고 소진)
        
        // when
        CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, 200L);  // 두 번째 시도
        
        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("SOLD_OUT");
        assertThat(result.getErrorMessage()).contains("모두 소진");
    }
    
    @Test
    @DisplayName("발급 순위 조회")
    void getIssueRank() {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);
        
        couponRedisRepository.issueCouponAtomic(1L, 100L);  // 1등
        couponRedisRepository.issueCouponAtomic(1L, 200L);  // 2등
        couponRedisRepository.issueCouponAtomic(1L, 300L);  // 3등
        
        // when
        Integer rank1 = couponRedisRepository.getIssueRank(1L, 100L);
        Integer rank2 = couponRedisRepository.getIssueRank(1L, 200L);
        Integer rank3 = couponRedisRepository.getIssueRank(1L, 300L);
        
        // then
        assertThat(rank1).isEqualTo(1);
        assertThat(rank2).isEqualTo(2);
        assertThat(rank3).isEqualTo(3);
    }
    
    @Test
    @DisplayName("선착순 Top 10 조회")
    void getTopIssuers() {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);

        // 원자적 카운터를 사용하므로 정확한 순서 보장
        for (long i = 1; i <= 20; i++) {
            couponRedisRepository.issueCouponAtomic(1L, i * 100);
        }

        // when
        List<Long> top10 = couponRedisRepository.getTopIssuers(1L, 10);

        // then
        assertThat(top10).hasSize(10);
        assertThat(top10.get(0)).isEqualTo(100L);  // 첫 번째 발급자
        assertThat(top10.get(9)).isEqualTo(1000L); // 열 번째 발급자
    }
    
    @Test
    @DisplayName("발급 여부 확인")
    void isAlreadyIssued() {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);
        couponRedisRepository.issueCouponAtomic(1L, 100L);
        
        // when & then
        assertThat(couponRedisRepository.isAlreadyIssued(1L, 100L)).isTrue();
        assertThat(couponRedisRepository.isAlreadyIssued(1L, 200L)).isFalse();
    }
    
    @Test
    @DisplayName("유저가 보유한 쿠폰 목록")
    void getUserCouponIds() {
        // given
        Coupon coupon1 = createTestCoupon(1L, 100);
        Coupon coupon2 = createTestCoupon(2L, 100);
        couponRedisRepository.initializeCoupon(coupon1);
        couponRedisRepository.initializeCoupon(coupon2);
        
        couponRedisRepository.issueCouponAtomic(1L, 100L);
        couponRedisRepository.issueCouponAtomic(2L, 100L);
        
        // when
        List<Long> couponIds = couponRedisRepository.getUserCouponIds(100L);
        
        // then
        assertThat(couponIds).hasSize(2);
        assertThat(couponIds).containsExactlyInAnyOrder(1L, 2L);
    }
    
    @Test
    @DisplayName("동시 발급 100명 - 재고 100개")
    void concurrentIssue100Users() throws InterruptedException {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);
        
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // when: 동시 요청
        for (long i = 1; i <= threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, userId);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        
        // then
        assertThat(successCount.get()).isEqualTo(100);  // 정확히 100명 발급
        
        Integer remainingStock = couponRedisRepository.getCouponStock(1L).orElse(-1);
        assertThat(remainingStock).isEqualTo(0);  // 재고 0
    }
    
    @Test
    @DisplayName("동시 발급 101명 - 재고 100개 (1명 실패)")
    void concurrentIssue101Users() throws InterruptedException {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);
        
        int threadCount = 101;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // when: 동시 요청
        for (long i = 1; i <= threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, userId);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        
        // then
        assertThat(successCount.get()).isEqualTo(100);  // 정확히 100명 발급
        assertThat(failCount.get()).isEqualTo(1);       // 1명 실패
        
        Integer remainingStock = couponRedisRepository.getCouponStock(1L).orElse(-1);
        assertThat(remainingStock).isEqualTo(0);  // 재고 0
    }
    
    @Test
    @DisplayName("순차성 보장 - 순서대로 순위 기록")
    void sequenceGuarantee() throws InterruptedException {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when: 동시 요청
        for (long i = 1; i <= threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    couponRedisRepository.issueCouponAtomic(1L, userId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then: 모든 유저가 1~50 사이의 순위를 가짐
        List<Long> topIssuers = couponRedisRepository.getTopIssuers(1L, 50);
        assertThat(topIssuers).hasSize(50);

        // 모든 순위가 올바르게 기록되었는지 확인
        for (int i = 0; i < topIssuers.size(); i++) {
            Long userId = topIssuers.get(i);
            Integer rank = couponRedisRepository.getIssueRank(1L, userId);
            assertThat(rank).isEqualTo(i + 1);  // 순위 일치
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 - 재고 50개, 동시 요청 100명 (정확히 50명만 성공)")
    void firstComeFirstServed_50Stock_100Requests() throws InterruptedException {
        // given
        Coupon coupon = createTestCoupon(1L, 50);  // 재고 50개
        couponRedisRepository.initializeCoupon(coupon);

        int threadCount = 100;  // 100명 동시 요청
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> successUserIds = new ArrayList<>();

        // when: 100명이 동시에 쿠폰 발급 요청
        for (long i = 1; i <= threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, userId);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                        synchronized (successUserIds) {
                            successUserIds.add(userId);
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(50);  // 정확히 50명만 성공
        assertThat(failCount.get()).isEqualTo(50);     // 나머지 50명은 실패

        Integer remainingStock = couponRedisRepository.getCouponStock(1L).orElse(-1);
        assertThat(remainingStock).isEqualTo(0);  // 재고 완전 소진

        // 선착순 50명의 순위가 올바르게 기록되었는지 확인
        List<Long> top50 = couponRedisRepository.getTopIssuers(1L, 50);
        assertThat(top50).hasSize(50);

        // 모든 성공자가 top50에 포함되는지 확인
        assertThat(successUserIds).hasSize(50);
        for (Long userId : successUserIds) {
            assertThat(top50).contains(userId);
        }
    }

    @Test
    @DisplayName("동시성 환경에서 중복 발급 방지 - 같은 유저의 10번 동시 요청")
    void preventDuplicateIssue_concurrent() throws InterruptedException {
        // given
        Coupon coupon = createTestCoupon(1L, 100);
        couponRedisRepository.initializeCoupon(coupon);

        Long sameUserId = 999L;  // 같은 유저
        int attemptCount = 10;   // 10번 동시 요청
        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch latch = new CountDownLatch(attemptCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // when: 같은 유저가 동시에 10번 발급 시도
        for (int i = 0; i < attemptCount; i++) {
            executor.submit(() -> {
                try {
                    CouponIssueResult result = couponRedisRepository.issueCouponAtomic(1L, sameUserId);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else if ("ALREADY_ISSUED".equals(result.getErrorCode())) {
                        duplicateCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then: 정확히 1번만 성공, 나머지는 중복 에러
        assertThat(successCount.get()).isEqualTo(1);   // 단 1번만 성공
        assertThat(duplicateCount.get()).isEqualTo(9); // 나머지 9번은 중복 에러

        // 재고는 1개만 차감되었는지 확인
        Integer remainingStock = couponRedisRepository.getCouponStock(1L).orElse(-1);
        assertThat(remainingStock).isEqualTo(99);  // 100 - 1 = 99

        // 발급 기록 확인
        assertThat(couponRedisRepository.isAlreadyIssued(1L, sameUserId)).isTrue();
        Integer rank = couponRedisRepository.getIssueRank(1L, sameUserId);
        assertThat(rank).isEqualTo(1);  // 첫 번째 발급자
    }
    
    private Coupon createTestCoupon(Long id, int maxIssueCount) {
        return Coupon.builder()
            .couponId(id)
            .name("테스트 쿠폰 " + id)
            .discountType(DiscountType.FIXED)
            .discountValue(BigDecimal.valueOf(1000))
            .maxIssueCount(maxIssueCount)
            .issuedCount(0)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(7))
            .status(CouponStatus.ACTIVE)
            .build();
    }
}
