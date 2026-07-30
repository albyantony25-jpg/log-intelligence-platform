package com.logplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only DTO (Data Transfer Object) returned by GET /logs/clusters.
 *
 * A "cluster" is a group of log entries that share the same:
 *   - serviceName
 *   - logLevel
 *   - 10-minute time bucket (timestamp rounded down to the nearest 10 minutes)
 *
 * Using a dedicated DTO instead of the raw LogEntry entity keeps the API
 * response stable even if the internal entity changes, and avoids leaking
 * persistence metadata (e.g. @Id, @Column) to API consumers.
 *
 * Immutable by design — all fields are set via the all-args constructor and
 * exposed only through getters.
 */
public class LogClusterResult {

    /**
     * The service that produced these log entries.
     * Example: "payment-service"
     */
    private final String serviceName;

    /**
     * Severity level shared by all entries in this cluster.
     * Only WARN and ERROR clusters are returned (INFO is filtered out).
     */
    private final String logLevel;

    /**
     * The start of the 10-minute bucket this cluster falls into.
     * Example: if entries have timestamps 10:03, 10:07, 10:09 → bucket = 10:00.
     * Serialised as an ISO-8601 string by Jackson (e.g. "2024-01-15T10:00:00").
     */
    private final LocalDateTime timeBucketStart;

    /**
     * Total number of log entries that belong to this cluster.
     * Results are sorted by this value descending (biggest problems first).
     */
    private final int count;

    /**
     * Up to 3 representative log messages sampled from this cluster.
     * Gives API consumers a quick sense of what is failing without
     * having to fetch the full log entries separately.
     */
    private final List<String> sampleMessages;

    /**
     * Z-score anomaly metric computed by LogClusterService.
     *
     * Interpretation:
     *   0.0      → average rate (or only one cluster in peer group)
     *   1.0–2.0  → elevated (1–2 standard deviations above peer mean)
     *   ≥ 2.0    → significant spike — warrants investigation
     *   < 0.0    → quieter than usual
     *
     * Set to 0.0 by default; populated via setAnomalyScore() after clustering.
     */
    private double anomalyScore = 0.0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public LogClusterResult(String serviceName,
                            String logLevel,
                            LocalDateTime timeBucketStart,
                            int count,
                            List<String> sampleMessages) {
        this.serviceName     = serviceName;
        this.logLevel        = logLevel;
        this.timeBucketStart = timeBucketStart;
        this.count           = count;
        this.sampleMessages  = List.copyOf(sampleMessages); // defensive copy
    }

    // -------------------------------------------------------------------------
    // Getters (Jackson uses these to serialise to JSON)
    // -------------------------------------------------------------------------

    public String getServiceName()            { return serviceName; }
    public String getLogLevel()               { return logLevel; }
    public LocalDateTime getTimeBucketStart() { return timeBucketStart; }
    public int getCount()                     { return count; }
    public List<String> getSampleMessages()   { return sampleMessages; }
    public double getAnomalyScore()           { return anomalyScore; }

    /** Called by LogClusterService after z-score computation. */
    public void setAnomalyScore(double anomalyScore) { this.anomalyScore = anomalyScore; }
}
