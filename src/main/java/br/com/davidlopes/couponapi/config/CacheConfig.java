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

@Configuration
@EnableCaching
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
