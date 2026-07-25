package com.logplatform.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * JPA entity that maps to the "log_entries" table in PostgreSQL.
 *
 * Each instance represents one structured log event emitted by a service.
 *
 * Annotations:
 *   @Entity         – tells Hibernate this class is a managed persistence entity
 *   @Table          – explicitly names the backing table (optional but recommended)
 *   @Id             – marks the primary key field
 *   @GeneratedValue – delegates ID generation to the database (IDENTITY strategy
 *                     uses PostgreSQL's SERIAL / auto-increment)
 *   @Column         – fine-grained column configuration (nullable, length, etc.)
 *   @NotBlank       – Bean Validation constraint; fails if the value is null,
 *                     empty, or whitespace-only (validated on POST)
 */
@Entity
@Table(name = "log_entries")
public class LogEntry {

    /** Auto-generated surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The name of the service that produced this log line.
     * Example values: "auth-service", "payment-gateway".
     */
    @NotBlank(message = "serviceName must not be blank")
    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    /**
     * Severity level of the log event.
     * Expected values: INFO, WARN, ERROR, DEBUG, TRACE.
     */
    @NotBlank(message = "logLevel must not be blank")
    @Column(name = "log_level", nullable = false, length = 16)
    private String logLevel;

    /** The human-readable log message body. */
    @NotBlank(message = "message must not be blank")
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * UTC timestamp of when the log event occurred.
     * Defaults to the moment the entity is persisted if not provided.
     */
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for JPA. */
    public LogEntry() {
    }

    public LogEntry(String serviceName, String logLevel, String message, LocalDateTime timestamp) {
        this.serviceName = serviceName;
        this.logLevel    = logLevel;
        this.message     = message;
        this.timestamp   = timestamp;
    }

    // -------------------------------------------------------------------------
    // Lifecycle callback
    // -------------------------------------------------------------------------

    /**
     * Automatically sets the timestamp to "now" just before the entity is
     * persisted, if the caller did not supply one.
     */
    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
