package com.logplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing in the Log Intelligence Platform.
 *
 * This enables the @Async annotation, which allows methods to be executed in a
 * separate thread pool, freeing up the calling thread (e.g., Tomcat HTTP threads).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Dedicated thread pool for asynchronous log ingestion.
     * Prevents log bursts from exhausting the main HTTP connection pool.
     */
    @Bean(name = "logIngestionExecutor")
    public Executor logIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Ingest-");
        executor.initialize();
        log.info("AsyncConfig: logIngestionExecutor initialized");
        return executor;
    }
}
