package com.logplatform.service;

import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service handling asynchronous log ingestion.
 */
@Service
public class LogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionService.class);
    
    private final LogEntryRepository logEntryRepository;

    public LogIngestionService(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    /**
     * Saves the log entry asynchronously in the background.
     * This ensures the HTTP POST endpoint returns immediately without blocking on DB I/O
     * or any potential subsequent operations (like LLM/clustering triggers in the future).
     */
    @Async("logIngestionExecutor")
    public CompletableFuture<LogEntry> ingestLog(LogEntry logEntry) {
        LogEntry saved = logEntryRepository.save(logEntry);
        log.debug("Asynchronously saved log entry: {}", saved.getId());
        return CompletableFuture.completedFuture(saved);
    }
}
