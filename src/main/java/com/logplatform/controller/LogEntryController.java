package com.logplatform.controller;

import com.logplatform.dto.LogClusterResult;
import com.logplatform.dto.LogClusterSummary;
import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import com.logplatform.service.GroqService;
import com.logplatform.service.LogClusterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller that exposes the /logs HTTP endpoints.
 *
 * Endpoints:
 *   POST /logs                  – ingest a new log entry
 *   GET  /logs                  – retrieve all log entries
 *   GET  /logs/clusters         – WARN/ERROR clusters (service + level + 10-min bucket)
 *   GET  /logs/clusters/summary – same clusters, each enriched with a Groq AI summary
 *
 * The controller is intentionally thin: it handles HTTP concerns only
 * (routing, serialisation, status codes).  Business logic lives in
 * dedicated service classes.
 */
@RestController
@RequestMapping("/logs")
public class LogEntryController {

    private final LogEntryRepository logEntryRepository;
    private final LogClusterService  logClusterService;
    private final GroqService        groqService;

    /**
     * Spring injects all three dependencies via this constructor.
     * No @Autowired annotation needed — Spring auto-detects single constructors.
     */
    public LogEntryController(LogEntryRepository logEntryRepository,
                              LogClusterService  logClusterService,
                              GroqService        groqService) {
        this.logEntryRepository = logEntryRepository;
        this.logClusterService  = logClusterService;
        this.groqService        = groqService;
    }

    // -------------------------------------------------------------------------
    // POST /logs
    // -------------------------------------------------------------------------

    /**
     * Accepts a JSON body representing a new log entry, validates it, persists it
     * to PostgreSQL, and returns the saved entity (including the generated ID and
     * auto-set timestamp).
     *
     * @Valid   – triggers Bean Validation on the incoming request body.
     *            If any @NotBlank constraint is violated, Spring returns a
     *            400 Bad Request with detailed error information automatically.
     *
     * @RequestBody – deserialises the incoming JSON payload into a LogEntry object.
     *
     * Returns HTTP 201 Created on success.
     */
    @PostMapping
    public ResponseEntity<LogEntry> createLogEntry(@Valid @RequestBody LogEntry logEntry) {
        LogEntry savedEntry = logEntryRepository.save(logEntry);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEntry);
    }

    // -------------------------------------------------------------------------
    // GET /logs
    // -------------------------------------------------------------------------

    /**
     * Retrieves all log entries from the database and returns them as a JSON array.
     *
     * Returns HTTP 200 OK with the list (empty array [] if no entries exist yet).
     *
     * For production use, consider adding pagination via Pageable to avoid
     * returning unbounded result sets.
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
     * Returns WARN and ERROR log entries grouped into clusters.
     *
     * A cluster represents a set of log entries sharing the same:
     *   - serviceName
     *   - logLevel  (only WARN or ERROR — INFO is too noisy to be useful signal)
     *   - 10-minute time bucket (timestamp floored to the nearest 10 minutes)
     *
     * Each cluster in the response includes:
     *   - serviceName      – which service produced the events
     *   - logLevel         – WARN or ERROR
     *   - timeBucketStart  – ISO-8601 start of the 10-minute window
     *   - count            – number of log entries in the cluster
     *   - sampleMessages   – up to 3 representative messages
     *
     * Results are sorted by count descending so the highest-volume incidents
     * appear first, making triage faster.
     *
     * Returns HTTP 200 OK with an empty array [] if no WARN/ERROR entries exist.
     *
     * Delegates entirely to LogClusterService — the controller only handles
     * the HTTP layer (routing + response wrapping).
     */
    @GetMapping("/clusters")
    public ResponseEntity<List<LogClusterResult>> getLogClusters() {
        List<LogClusterResult> clusters = logClusterService.computeClusters();
        return ResponseEntity.ok(clusters);
    }

    // -------------------------------------------------------------------------
    // GET /logs/clusters/summary
    // -------------------------------------------------------------------------

    /**
     * Returns the same WARN/ERROR clusters as GET /logs/clusters, but each
     * cluster is enriched with an "aiSummary" field — a 1-2 sentence plain-
     * English explanation generated by the Groq LLM (llama-3.3-70b-versatile).
     *
     * Error resilience:
     *   If the Groq API call fails for any cluster (bad key, rate limit, network
     *   issue), that cluster's aiSummary is set to "Summary unavailable" and
     *   processing continues.  The endpoint never returns a 5xx due to an AI
     *   failure.
     *
     * Performance note:
     *   Groq API calls are made sequentially.  For large cluster sets, consider
     *   using CompletableFuture / virtual threads to parallelise the LLM calls.
     *
     * Returns HTTP 200 OK with an empty array [] if no WARN/ERROR clusters exist.
     */
    @GetMapping("/clusters/summary")
    public ResponseEntity<List<LogClusterSummary>> getLogClusterSummaries() {
        List<LogClusterResult> clusters = logClusterService.computeClusters();

        List<LogClusterSummary> summaries = clusters.stream()
                .map(cluster -> {
                    // GroqService.summarize() never throws — it returns the
                    // fallback string on any error, so no try/catch needed here.
                    String aiSummary = groqService.summarize(cluster);
                    return new LogClusterSummary(cluster, aiSummary);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }
}
