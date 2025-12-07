package h99.ecommerce.infrastructure.repository.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.domain.coupon.Coupon;
import h99.ecommerce.dto.CouponIssueResult;
import h99.ecommerce.repository.CouponRedisRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCouponRepository implements CouponRedisRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private DefaultRedisScript<String> couponIssueScript;
    
    @PostConstruct
    public void init() {
        // Lua Script 로드
        couponIssueScript = new DefaultRedisScript<>();
        couponIssueScript.setScriptSource(
            new ResourceScriptSource(new ClassPathResource("lua/coupon_issue_v2.lua"))
        );
        couponIssueScript.setResultType(String.class);
        
        log.info("Lua Script 로드 완료: coupon_issue_v2.lua");
    }
    
    @Override
    public void initializeCoupon(Coupon coupon) {
        String couponId = coupon.getCouponId().toString();
        
        // 1. 메타 정보 저장 (Hash)
        Map<String, String> meta = new HashMap<>();
        meta.put("name", coupon.getName());
        meta.put("maxIssueCount", String.valueOf(coupon.getMaxIssueCount()));
        meta.put("startAt", String.valueOf(toEpochMilli(coupon.getStartAt())));
        meta.put("endAt", String.valueOf(toEpochMilli(coupon.getEndAt())));
        meta.put("status", coupon.getStatus().name());
        
        String metaKey = "coupon:meta:" + couponId;
        redisTemplate.opsForHash().putAll(metaKey, meta);
        
        // 2. 재고 초기화 (String)
        String stockKey = "coupon:stock:" + couponId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(coupon.getMaxIssueCount()));
        
        // 3. TTL 설정
        long metaTTL = calculateTTL(coupon.getEndAt().plusDays(1));
        redisTemplate.expire(metaKey, metaTTL, TimeUnit.SECONDS);
        redisTemplate.expire(stockKey, metaTTL, TimeUnit.SECONDS);
        
        log.info("Redis 쿠폰 초기화 완료 - couponId: {}, stock: {}, TTL: {}초", 
            couponId, coupon.getMaxIssueCount(), metaTTL);
    }
    
    @Override
    public CouponIssueResult issueCouponAtomic(Long couponId, Long userId) {
        try {
            // Lua Script 실행
            String result = redisTemplate.execute(
                couponIssueScript,
                Collections.singletonList(couponId.toString()),
                userId.toString(),
                String.valueOf(System.currentTimeMillis())
            );
            
            // JSON 파싱
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
            boolean success = (Boolean) resultMap.get("success");
            
            if (success) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resultMap.get("data");
                return CouponIssueResult.success(
                    couponId,
                    userId,
                    ((Number) data.get("issuedAt")).longValue(),
                    ((Number) data.get("remainingStock")).intValue(),
                    ((Number) data.get("rank")).intValue()
                );
            } else {
                String error = (String) resultMap.get("error");
                String message = (String) resultMap.get("message");
                return CouponIssueResult.failure(error, message);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Lua Script 결과 파싱 실패", e);
            return CouponIssueResult.failure("SCRIPT_ERROR", "쿠폰 발급 처리 중 오류가 발생했습니다");
        }
    }
    
    @Override
    public Optional<Integer> getCouponStock(Long couponId) {
        String stockKey = "coupon:stock:" + couponId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        
        if (stock == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(Integer.parseInt(stock));
        } catch (NumberFormatException e) {
            log.error("재고 데이터 형식 오류 - couponId: {}, value: {}", couponId, stock);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean isAlreadyIssued(Long couponId, Long userId) {
        String issuedSetKey = "coupon:issued:" + couponId;
        String userKey = "user:" + userId;
        
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedSetKey, userKey);
        return Boolean.TRUE.equals(isMember);
    }
    
    @Override
    public Integer getIssueRank(Long couponId, Long userId) {
        String logKey = "coupon:issue:log:" + couponId;
        String userKey = "user:" + userId;
        
        Long rank = redisTemplate.opsForZSet().rank(logKey, userKey);
        return rank != null ? rank.intValue() + 1 : null; // 1-based
    }
    
    @Override
    public List<Long> getTopIssuers(Long couponId, int limit) {
        String logKey = "coupon:issue:log:" + couponId;
        
        Set<String> topUsers = redisTemplate.opsForZSet().range(logKey, 0, limit - 1);
        
        if (topUsers == null || topUsers.isEmpty()) {
            return Collections.emptyList();
        }
        
        return topUsers.stream()
            .map(s -> s.replace("user:", ""))
            .map(Long::parseLong)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Long> getUserCouponIds(Long userId) {
        String userCouponsKey = "user:coupons:" + userId;
        
        Set<String> couponIds = redisTemplate.opsForSet().members(userCouponsKey);
        
        if (couponIds == null || couponIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return couponIds.stream()
            .map(Long::parseLong)
            .collect(Collectors.toList());
    }
    
    @Override
    public void setExpiration(String key, long seconds) {
        redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }
    
    @Override
    public void updateCouponStatus(Long couponId, String status) {
        String metaKey = "coupon:meta:" + couponId;
        redisTemplate.opsForHash().put(metaKey, "status", status);
    }
    
    // ========== 유틸리티 메서드 ==========
    
    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    
    private long calculateTTL(LocalDateTime targetTime) {
        Duration duration = Duration.between(LocalDateTime.now(), targetTime);
        long seconds = duration.getSeconds();
        return Math.max(seconds, 0); // 음수 방지
    }
}
