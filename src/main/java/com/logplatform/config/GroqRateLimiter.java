package com.logplatform.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token-bucket rate limiter for outbound Groq API calls.
 *
 * Why rate-limit?
 *   The Groq free tier allows ~30 requests per minute. Without a limiter,
 *   a burst of cluster-summary requests could exhaust the quota immediately,
 *   causing all subsequent calls to return HTTP 429 for the rest of the minute.
 *
 * Algorithm — Token Bucket:
 *   - Capacity: groq.rate-limit.requests-per-minute (default 25, conservative
 *     buffer below the Groq 30/min limit).
 *   - Refill: greedy — tokens are added continuously (not in one batch at the
 *     end of the window), so short bursts are allowed as long as the bucket
 *     has tokens.
 *   - On rate-limit hit: tryConsume() returns false → GroqService returns
 *     "Summary unavailable" immediately without blocking the caller.
 *
 * This bean is a singleton; the Bucket instance is thread-safe.
 */
@Component
public class GroqRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(GroqRateLimiter.class);

    private final Bucket bucket;
    private final int requestsPerMinute;

    public GroqRateLimiter(
            @Value("${groq.rate-limit.requests-per-minute:25}") int requestsPerMinute) {

        this.requestsPerMinute = requestsPerMinute;

        // Greedy refill: tokens are replenished continuously over the minute window.
        // e.g. 25/min → 1 token every 2.4 seconds
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));

        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();

        log.info("GroqRateLimiter initialised: {} requests/minute", requestsPerMinute);
    }

    /**
     * Attempts to consume one token from the bucket.
     *
     * @return true  – token consumed, call is allowed.
     *         false – bucket empty, call is rate-limited; caller should degrade gracefully.
     */
    public boolean tryConsume() {
        return bucket.tryConsume(1);
    }

    /** Returns the configured request-per-minute limit (for logging/monitoring). */
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    /** Returns current number of available tokens (useful in health checks). */
    public long getAvailableTokens() {
        return bucket.getAvailableTokens();
    }
}
