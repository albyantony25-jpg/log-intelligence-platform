package com.logplatform.controller;

import com.logplatform.dto.LogClusterResult;
import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import com.logplatform.service.LogClusterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the /logs HTTP endpoints.
 *
 * Endpoints:
 *   POST /logs          – ingest a new log entry
 *   GET  /logs          – retrieve all log entries
 *   GET  /logs/clusters – retrieve WARN/ERROR clusters grouped by service,
 *                         level, and 10-minute time bucket
 *
 * The controller is intentionally thin: it handles HTTP concerns only
 * (routing, serialisation, status codes).  Business logic lives in
 * dedicated service classes (e.g. LogClusterService).
 */
@RestController
@RequestMapping("/logs")
public class LogEntryController {

    private final LogEntryRepository logEntryRepository;
    private final LogClusterService  logClusterService;

    /**
     * Spring injects both dependencies via this single constructor.
     * No @Autowired annotation needed — Spring auto-detects single constructors.
     */
    public LogEntryController(LogEntryRepository logEntryRepository,
                              LogClusterService  logClusterService) {
        this.logEntryRepository = logEntryRepository;
        this.logClusterService  = logClusterService;
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
}
