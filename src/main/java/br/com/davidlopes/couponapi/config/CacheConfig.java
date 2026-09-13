package br.com.davidlopes.couponapi.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * {@code order = 0} makes the caching advisor wrap OUTSIDE the transaction advisor, which
 * Spring Boot's auto-configured transaction management leaves at its own default
 * ({@code Ordered.LOWEST_PRECEDENCE}) unless told otherwise. With caching outermost,
 * {@code @CacheEvict} on {@code CouponService.delete()} only fires once the whole advised
 * call — transaction commit included — has already returned successfully, closing the race
 * where a concurrent read between evict and commit could repopulate the cache with the
 * not-yet-deleted value. Without an explicit order here, both advisors default to the same
 * precedence and their relative ordering is unspecified.
 */
@Configuration
@EnableCaching(order = 0)
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        // GenericJackson2JsonRedisSerializer builds its own internal Jackson 2 ObjectMapper
        // (independent of this app's own Jackson 3 setup) and does not register JSR-310 by
        // default, so java.time types like LocalDateTime fail to serialize. .configure(...)
        // adds the module to that internal mapper without disturbing the default-typing setup
        // the no-arg constructor already establishes for correct polymorphic deserialization.
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer()
            .configure(mapper -> mapper.registerModule(new JavaTimeModule()));

        return builder -> builder.cacheDefaults(
            RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(serializer)));
    }
}
