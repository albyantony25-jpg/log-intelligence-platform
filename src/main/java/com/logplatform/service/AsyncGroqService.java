package com.logplatform.service;

import com.logplatform.dto.LogClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Async wrapper around {@link GroqService#summarize(LogClusterResult)}.
 *
 * Problem:
 *   GET /logs/clusters/summary calls GroqService.summarize() once per cluster.
 *   With N clusters this takes N × ~1-2 s (sequential HTTP round-trips to Groq).
 *   For 10 clusters that's 10-20 seconds — unacceptable latency.
 *
 * Solution:
 *   This service exposes a single method, summarizeAsync(), annotated with
 *   @Async.  Spring wraps each invocation in a CompletableFuture that runs on a
 *   shared thread pool, letting all N AI calls execute concurrently.
 *   The controller uses CompletableFuture.allOf() to wait for all results before
 *   returning the HTTP response.  Total latency ≈ the slowest single Groq call
 *   instead of the sum of all calls.
 *
 * Why a separate bean?
 *   @Async only takes effect when called through a Spring proxy (i.e., from a
 *   *different* bean).  Self-calls within the same class bypass the proxy and
 *   run synchronously.  Splitting the async logic into its own @Service class
 *   is the idiomatic Spring workaround.
 *
 * Thread pool:
 *   Spring Boot auto-configures a SimpleAsyncTaskExecutor for @Async unless you
 *   define a custom ThreadPoolTaskExecutor bean.  For production load, define one
 *   in a @Configuration class and annotate it @Primary so Spring picks it up.
 */
@Service
public class AsyncGroqService {

    private static final Logger log = LoggerFactory.getLogger(AsyncGroqService.class);

    private final GroqService groqService;

    public AsyncGroqService(GroqService groqService) {
        this.groqService = groqService;
    }

    /**
     * Calls {@link GroqService#summarize(LogClusterResult)} on a separate thread
     * from Spring's async executor pool and returns a {@link CompletableFuture}
     * that the caller can join.
     *
     * The underlying GroqService already handles all error cases gracefully
     * (blank key, rate limit, network failure) by returning "Summary unavailable",
     * so this method never completes exceptionally.
     *
     * @param cluster the log cluster to summarise
     * @return future that completes with the AI summary string
     */
    @Async
    public CompletableFuture<String> summarizeAsync(LogClusterResult cluster) {
        log.debug("AsyncGroqService: starting async summarize for [{}/{}] on thread {}",
                cluster.getServiceName(), cluster.getLogLevel(),
                Thread.currentThread().getName());

        String summary = groqService.summarize(cluster);

        return CompletableFuture.completedFuture(summary);
    }
}
