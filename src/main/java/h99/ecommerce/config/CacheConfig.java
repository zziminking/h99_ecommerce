package h99.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper)
                        )
                )
                .disableCachingNullValues(); // null 값은 캐싱하지 않음

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Jitter적용
        cacheConfigurations.put("popularProductsByView",
                defaultConfig.entryTtl(getTtlWithJitter(Duration.ofHours(1), Duration.ofMinutes(5))));

        cacheConfigurations.put("popularProductsByOrder",
                defaultConfig.entryTtl(getTtlWithJitter(Duration.ofHours(1), Duration.ofMinutes(5))));

        cacheConfigurations.put("product",
                defaultConfig.entryTtl(getTtlWithJitter(Duration.ofMinutes(30), Duration.ofMinutes(2))));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Jitter를 적용한 TTL 생성 Cache Stampede 방지: 캐시 만료 시점을 분산시켜 동시 만료 방지
     */
    private Duration getTtlWithJitter(Duration baseTtl, Duration jitterRange) {
        long baseSeconds = baseTtl.getSeconds();
        long jitterSeconds = jitterRange.getSeconds();

        long randomJitter = (long) (Math.random() * jitterSeconds * 2) - jitterSeconds;
        long finalTtl = baseSeconds + randomJitter;

        if (finalTtl < 10) {
            finalTtl = 10;
        }

        return Duration.ofSeconds(finalTtl);
    }

}
