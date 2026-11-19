package Microsserv52.product.config;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheConfig.class);

    private final boolean enabled;
    private final long ttlSeconds;
    private final RedisConnectionFactory connectionFactory;
    private final MeterRegistry meterRegistry;

    public CacheConfig(@Value("${platform.cache.enabled:false}") boolean enabled,
                       @Value("${platform.cache.ttl-seconds:60}") long ttlSeconds,
                       RedisConnectionFactory connectionFactory,
                       MeterRegistry meterRegistry) {
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
        this.connectionFactory = connectionFactory;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        if (!enabled) {
            return new NoOpCacheManager();
        }
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(ttlSeconds))
            .disableCachingNullValues()
            .prefixCacheNameWith("product:");
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory,
            configurer -> configurer.immediateWrites());
        return RedisCacheManager.builder(writer)
            .cacheDefaults(configuration)
            .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                record("get", cache, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                record("put", cache, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                record("evict", cache, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                record("clear", cache, exception);
            }

            private void record(String operation, Cache cache, RuntimeException exception) {
                meterRegistry.counter("product.cache.errors",
                    "cache", cache.getName(),
                    "operation", operation).increment();
                LOGGER.warn("Cache {} failed for {}: {}", operation, cache.getName(),
                    exception.getClass().getSimpleName());
            }
        };
    }
}
