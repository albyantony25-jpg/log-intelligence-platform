package com.logplatform.controller;

import com.logplatform.dto.LogClusterResult;
import com.logplatform.dto.LogClusterSummary;
import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import com.logplatform.service.AsyncGroqService;
import com.logplatform.service.LogClusterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * REST controller that exposes the /logs HTTP endpoints.
 *
 * Endpoints:
 *   POST /logs                  – ingest a new log entry (202 Accepted, non-blocking)
 *   GET  /logs                  – retrieve all log entries
 *   GET  /logs/clusters         – WARN/ERROR clusters (service + level + 10-min bucket)
 *   GET  /logs/clusters/summary – clusters enriched with parallel AI summaries
 *
 * Async design (GET /logs/clusters/summary):
 *   Previously: N clusters × sequential Groq call ≈ N × 1–2 s latency
 *   Now:        All N Groq calls fired in parallel via AsyncGroqService;
 *               total latency ≈ slowest single call (~1–2 s regardless of N)
 */
@RestController
@RequestMapping("/logs")
public class LogEntryController {

    private static final Logger log = LoggerFactory.getLogger(LogEntryController.class);

    private final LogEntryRepository logEntryRepository;
    private final LogClusterService  logClusterService;
    private final AsyncGroqService   asyncGroqService;

    public LogEntryController(LogEntryRepository logEntryRepository,
                              LogClusterService  logClusterService,
                              AsyncGroqService   asyncGroqService) {
        this.logEntryRepository = logEntryRepository;
        this.logClusterService  = logClusterService;
        this.asyncGroqService   = asyncGroqService;
    }

    // -------------------------------------------------------------------------
    // POST /logs — async-friendly ingestion
    // -------------------------------------------------------------------------

    /**
     * Accepts a new log entry, validates it, and persists it to PostgreSQL.
     *
     * Returns HTTP 202 Accepted (instead of 201 Created) to signal that the
     * entry has been queued/stored and any background enrichment (clustering,
     * AI analysis) will happen asynchronously on the read path.
     *
     * This makes the ingestion endpoint non-blocking: callers don't wait for
     * any LLM processing; they can fire-and-forget at high throughput.
     */
    @PostMapping
    public ResponseEntity<LogEntry> createLogEntry(@Valid @RequestBody LogEntry logEntry) {
        LogEntry savedEntry = logEntryRepository.save(logEntry);
        // 202 Accepted: entry is stored; async analysis happens on the read path
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(savedEntry);
    }

    // -------------------------------------------------------------------------
    // GET /logs
    // -------------------------------------------------------------------------

    /**
     * Retrieves all log entries from the database and returns them as a JSON array.
     * Returns HTTP 200 OK (empty array [] if no entries exist yet).
     */
    @GetMapping
    public ResponseEntity<List<LogEntry>> getAllLogEntries() {
        List<LogEntry> entries = logEntryRepository.findAll();
        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // GET /logs/clusters
    // -------------------------------------------------------------------------

    /**
     * Returns WARN/ERROR log clusters sorted by count descending.
     * Each cluster now includes an {@code anomalyScore} z-score field.
     */
    @GetMapping("/clusters")
    public ResponseEntity<List<LogClusterResult>> getLogClusters() {
        List<LogClusterResult> clusters = logClusterService.computeClusters();
        return ResponseEntity.ok(clusters);
    }

    // -------------------------------------------------------------------------
    // GET /logs/clusters/summary — parallel AI enrichment
    // -------------------------------------------------------------------------

    /**
     * Returns WARN/ERROR clusters each enriched with a Groq AI summary.
     *
     * Parallel execution strategy:
     *   1. Compute all clusters synchronously (fast — in-memory grouping).
     *   2. Fan-out: fire one AsyncGroqService.summarizeAsync() call per cluster.
     *      Each call runs on a Spring @Async thread-pool thread concurrently.
     *   3. CompletableFuture.allOf() blocks until ALL summaries have returned
     *      (or timed out / failed gracefully with "Summary unavailable").
     *   4. Collect results and return the enriched list.
     *
     * Before this change: 10 clusters × ~1.5 s = ~15 s total latency.
     * After  this change: max(10 × ~1.5 s) ≈ ~1.5 s total latency.
     */
    @GetMapping("/clusters/summary")
    public ResponseEntity<List<LogClusterSummary>> getLogClusterSummaries() {
        List<LogClusterResult> clusters = logClusterService.computeClusters();

        if (clusters.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Fan-out: one CompletableFuture per cluster (each runs on async thread pool)
        List<CompletableFuture<String>> futures = clusters.stream()
                .map(asyncGroqService::summarizeAsync)
                .collect(Collectors.toList());

        // Wait for all AI calls to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Zip clusters with their summaries
        List<LogClusterSummary> summaries = IntStream.range(0, clusters.size())
                .mapToObj(i -> {
                    String aiSummary = futures.get(i).join(); // already done — instant
                    return new LogClusterSummary(clusters.get(i), aiSummary);
                })
                .collect(Collectors.toList());

        log.debug("LogEntryController: returned {} summaries (parallel AI calls)",
                summaries.size());

        return ResponseEntity.ok(summaries);
    }
}
