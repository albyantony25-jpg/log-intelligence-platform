package com.logplatform.service;

import com.logplatform.dto.LogClusterResult;
import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for log clustering.
 *
 * Responsibility:
 *   Fetch all LogEntry records from the database and group them into
 *   "clusters" — buckets that share the same serviceName, logLevel, and
 *   10-minute time window.  Only WARN and ERROR entries are clustered;
 *   INFO is excluded because it is too noisy to be actionable signal.
 *
 * Design notes:
 *   - @Service marks this as a Spring-managed singleton bean so it can be
 *     injected into controllers or other services via constructor injection.
 *   - All grouping is done in-memory using the Java Streams API.  This is
 *     intentional: the data set is expected to be moderate in size, and
 *     doing the aggregation in Java keeps the repository interface simple and
 *     the SQL generic.  For very large data sets, consider pushing the GROUP BY
 *     down to the database via a @Query or a native SQL projection.
 *   - The 10-minute bucket is computed by zeroing out seconds/nanoseconds and
 *     rounding the minute field down to the nearest multiple of 10.
 */
@Service
public class LogClusterService {

    /**
     * Only log levels in this set are included in clustering results.
     * INFO is deliberately excluded — it is too frequent to surface actionable
     * problems in a cluster view.
     */
    private static final Set<String> ALERTING_LEVELS = Set.of("WARN", "ERROR");

    /** Maximum number of sample messages to include per cluster. */
    private static final int MAX_SAMPLES = 3;

    private final LogEntryRepository logEntryRepository;

    /**
     * Constructor injection — preferred over @Autowired on fields because it
     * makes the dependency explicit and enables straightforward unit testing
     * without a Spring context (just pass a mock repository).
     */
    public LogClusterService(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes and returns all WARN/ERROR log clusters.
     *
     * Algorithm:
     *   1. Load all LogEntry rows from the database.
     *   2. Filter to WARN and ERROR entries only.
     *   3. Group by a composite key: (serviceName, logLevel, 10-min bucket).
     *   4. For each group, build a LogClusterResult with count + up to 3 samples.
     *   5. Sort the clusters by count descending (highest-volume problems first).
     *
     * @return sorted list of clusters, never null, may be empty.
     */
    public List<LogClusterResult> computeClusters() {

        List<LogEntry> allEntries = logEntryRepository.findAll();

        // --- Step 1: filter to alerting levels only --------------------------
        // Entries with a null timestamp are bucketed at LocalDateTime.MIN to
        // avoid a NullPointerException; they will appear at the end after sorting.

        // --- Step 2: group by composite key ----------------------------------
        Map<ClusterKey, List<LogEntry>> grouped = allEntries.stream()
                .filter(e -> ALERTING_LEVELS.contains(e.getLogLevel()))
                .collect(Collectors.groupingBy(e -> new ClusterKey(
                        e.getServiceName(),
                        e.getLogLevel(),
                        toBucket(e.getTimestamp())
                )));

        // --- Step 3: transform each group into a LogClusterResult ------------
        return grouped.entrySet().stream()
                .map(entry -> {
                    ClusterKey     key     = entry.getKey();
                    List<LogEntry> entries = entry.getValue();

                    // Pick up to MAX_SAMPLES messages from the group
                    List<String> samples = entries.stream()
                            .limit(MAX_SAMPLES)
                            .map(LogEntry::getMessage)
                            .collect(Collectors.toList());

                    return new LogClusterResult(
                            key.serviceName(),
                            key.logLevel(),
                            key.timeBucketStart(),
                            entries.size(),
                            samples
                    );
                })
                // Sort by count descending — biggest incident clusters surface first
                .sorted(Comparator.comparingInt(LogClusterResult::getCount).reversed())
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Rounds a LocalDateTime DOWN to the nearest 10-minute boundary.
     *
     * Examples:
     *   10:03:47  →  10:00:00
     *   10:17:22  →  10:10:00
     *   10:59:59  →  10:50:00
     *
     * If the timestamp is null (data quality issue), returns LocalDateTime.MIN
     * so the entry is still grouped rather than causing an exception.
     */
    private static LocalDateTime toBucket(LocalDateTime ts) {
        if (ts == null) return LocalDateTime.MIN;
        int bucketMinute = (ts.getMinute() / 10) * 10; // floor to nearest 10
        return ts.withMinute(bucketMinute)
                 .withSecond(0)
                 .withNano(0);
    }

    // -------------------------------------------------------------------------
    // Composite grouping key
    // -------------------------------------------------------------------------

    /**
     * Immutable record used as the Map key for grouping log entries.
     *
     * Java records automatically generate:
     *   - A canonical constructor
     *   - equals() and hashCode() based on all three components
     *   - toString()
     *   - Accessor methods serviceName(), logLevel(), timeBucketStart()
     *
     * This ensures that two entries with the same service, level, and
     * 10-minute window are placed in the same group.
     */
    private record ClusterKey(
            String serviceName,
            String logLevel,
            LocalDateTime timeBucketStart
    ) {}
}
