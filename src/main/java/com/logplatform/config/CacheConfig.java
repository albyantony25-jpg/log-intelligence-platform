package com.logplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis caching configuration for the Log Intelligence Platform.
 *
 * What this class does:
 *   1. Enables Spring's Cache abstraction (@EnableCaching) so that
 *      @Cacheable/@CacheEvict annotations on service methods take effect.
 *   2. Configures a RedisCacheManager as the cache provider with:
 *        - 1-hour TTL for all cached entries
 *        - String keys (human-readable in Redis)
 *        - JSON-serialized values (survives app restarts without version mismatch)
 *        - Null values excluded (never cache a null result)
 *   3. Provides a CacheErrorHandler that silently degrades rather than crashing
 *      when Redis is unavailable.  If the Redis connection fails:
 *        - GET errors  → cache miss, the real method runs instead
 *        - PUT errors  → result not stored, no impact on caller
 *        - EVICT/CLEAR → ignored silently
 *      The application continues to work 100% correctly without Redis; it just
 *      loses the performance benefit of caching (every Groq call is a real HTTP
 *      call).  This is the desired "graceful degradation" behaviour.
 *
 * Why CachingConfigurer?
 *   CachingConfigurer is Spring's hook for overriding cache infrastructure beans.
 *   By implementing it, CacheConfig.errorHandler() replaces the default
 *   SimpleLoggingCacheErrorHandler with our custom no-op fallback handler.
 *
 * Redis connection:
 *   The Lettuce client (bundled with spring-boot-starter-data-redis) connects
 *   lazily — the TCP connection is established on the first cache operation,
 *   not at startup.  This means the app starts successfully even if Redis is
 *   not yet available.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Name of the cache that stores Groq AI summaries. */
    public static final String GROQ_CACHE = "groq-summaries";

    /**
     * Builds the RedisCacheManager with a 1-hour TTL.
     *
     * Serialization choices:
     *   - Keys: StringRedisSerializer → plain UTF-8 strings (easy to inspect in redis-cli)
     *   - Values: GenericJackson2JsonRedisSerializer → JSON (human-readable, survives restarts)
     *
     * @param factory auto-configured by Spring Boot from spring.data.redis.* properties
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))          // cached summaries expire after 1 hour
                .disableCachingNullValues()              // never store a null result
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(cacheConfig)
                .build();
    }

    /**
     * Custom CacheErrorHandler — converts Redis exceptions into warnings.
     *
     * Without this, any Redis connection problem (Redis down, network flap,
     * timeout) would propagate as a RuntimeException and crash the API endpoint.
     * With this handler, the error is logged once and the cache operation is
     * simply skipped, keeping the endpoint fully functional.
     *
     * This is the recommended pattern for optional caching: treat the cache as a
     * best-effort performance layer, not a hard dependency.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                // A GET failure means a cache miss — the @Cacheable method runs normally.
                log.warn("Redis GET failed (cache={}  key={}) — falling back to direct call: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                // A PUT failure means the result won't be stored this time — no impact on caller.
                log.warn("Redis PUT failed (cache={}  key={}) — result not cached: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis EVICT failed (cache={}  key={}): {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis CLEAR failed (cache={}): {}", cache.getName(), e.getMessage());
            }
        };
    }
}
