package com.logplatform.controller;

import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the /logs HTTP endpoints.
 *
 * @RestController  – combines @Controller + @ResponseBody; every method's return
 *                   value is serialised directly to the HTTP response body as JSON
 *                   (via Jackson, which is on the classpath via spring-boot-starter-web).
 *
 * @RequestMapping  – sets the base path for all endpoints in this controller.
 *
 * Constructor injection is used (preferred over @Autowired on fields) because it
 * makes dependencies explicit and enables easier unit testing.
 */
@RestController
@RequestMapping("/logs")
public class LogEntryController {

    private final LogEntryRepository logEntryRepository;

    public LogEntryController(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
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
}
