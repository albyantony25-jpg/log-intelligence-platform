package com.logplatform.service;

import com.logplatform.dto.LogClusterResult;
import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LogClusterService}.
 *
 * Pure unit tests — no Spring context, no database.
 * {@link LogEntryRepository} is mocked with Mockito.
 * {@link InjectMocks} wires the mock via constructor injection.
 *
 * Coverage areas:
 *   - computeClusters() filtering, grouping, bucketing, sorting
 *   - toBucket() edge cases: exact boundaries, cross-hour, null timestamps
 *   - MAX_SAMPLES cap (3 messages per cluster)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogClusterService")
class LogClusterServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private LogClusterService logClusterService;

    // ---- Factory helpers ---------------------------------------------------

    /** Creates a LogEntry at 2024-01-15 10:{minute}:00. */
    private static LogEntry entry(String service, String level, String msg, int minute) {
        return new LogEntry(service, level, msg,
                LocalDateTime.of(2024, 1, 15, 10, minute, 0));
    }

    private static LogEntry entry(String service, String level, String msg) {
        return entry(service, level, msg, 0);
    }

    // =========================================================================
    // Empty / INFO-only inputs
    // =========================================================================

    @Nested
    @DisplayName("given no entries or INFO-only entries")
    class WhenNoClusters {

        @Test
        @DisplayName("returns empty list when repository is empty")
        void computeClusters_whenRepositoryEmpty_returnsEmptyList() {
            when(logEntryRepository.findAll()).thenReturn(Collections.emptyList());

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).isEmpty();
            verify(logEntryRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("returns empty list when all entries are INFO")
        void computeClusters_whenOnlyInfoEntries_returnsEmptyList() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service",    "INFO", "User logged in"),
                    entry("payment-service", "INFO", "Payment processed")
            ));

            assertThat(logClusterService.computeClusters()).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for DEBUG and TRACE entries")
        void computeClusters_whenOnlyDebugTrace_returnsEmptyList() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "DEBUG", "Token validated"),
                    entry("auth-service", "TRACE", "Entering doAuth")
            ));

            assertThat(logClusterService.computeClusters()).isEmpty();
        }
    }

    // =========================================================================
    // Single entry
    // =========================================================================

    @Nested
    @DisplayName("given a single actionable entry")
    class WhenSingleEntry {

        @Test
        @DisplayName("single ERROR entry produces exactly one cluster")
        void computeClusters_singleError_oneCluster() {
            when(logEntryRepository.findAll()).thenReturn(
                    List.of(entry("payment-service", "ERROR", "Gateway timeout")));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(1);
            LogClusterResult c = result.get(0);
            assertThat(c.getServiceName()).isEqualTo("payment-service");
            assertThat(c.getLogLevel()).isEqualTo("ERROR");
            assertThat(c.getCount()).isEqualTo(1);
            assertThat(c.getSampleMessages()).containsExactly("Gateway timeout");
        }

        @Test
        @DisplayName("single WARN entry produces exactly one cluster")
        void computeClusters_singleWarn_oneCluster() {
            when(logEntryRepository.findAll()).thenReturn(
                    List.of(entry("auth-service", "WARN", "Session expiring")));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLogLevel()).isEqualTo("WARN");
        }
    }

    // =========================================================================
    // Grouping correctness
    // =========================================================================

    @Nested
    @DisplayName("grouping logic")
    class GroupingLogic {

        @Test
        @DisplayName("two entries in the same 10-min window are merged (count=2)")
        void computeClusters_sameBucket_groupedTogether() {
            // minute=3 and minute=7 both fall in bucket 10:00
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("payment-service", "ERROR", "Timeout txn_001", 3),
                    entry("payment-service", "ERROR", "Timeout txn_002", 7)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("entries in different 10-min windows produce separate clusters")
        void computeClusters_differentWindows_separateClusters() {
            // minute=3 → bucket 10:00, minute=15 → bucket 10:10
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("payment-service", "ERROR", "Error at 10:03", 3),
                    entry("payment-service", "ERROR", "Error at 10:15", 15)
            ));

            assertThat(logClusterService.computeClusters()).hasSize(2);
        }

        @Test
        @DisplayName("same window different services produce separate clusters")
        void computeClusters_differentServices_separateClusters() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service",    "ERROR", "Auth error",    2),
                    entry("payment-service", "ERROR", "Payment error", 2)
            ));

            assertThat(logClusterService.computeClusters()).hasSize(2);
        }

        @Test
        @DisplayName("same service and window but different levels produce separate clusters")
        void computeClusters_differentLevelsSameWindow_separateClusters() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "ERROR", "Auth failed",      5),
                    entry("auth-service", "WARN",  "Rate limit hit",   5)
            ));

            assertThat(logClusterService.computeClusters()).hasSize(2);
        }

        @Test
        @DisplayName("INFO entries mixed in are excluded; only WARN and ERROR returned")
        void computeClusters_mixedLevels_onlyWarnErrorReturned() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "INFO",  "Login success",   0),
                    entry("auth-service", "WARN",  "Rate limit hit",  0),
                    entry("auth-service", "ERROR", "Token invalid",   0),
                    entry("auth-service", "DEBUG", "Entering method", 0)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(LogClusterResult::getLogLevel)
                    .containsExactlyInAnyOrder("WARN", "ERROR");
        }
    }

    // =========================================================================
    // 10-minute bucket boundary cases
    // =========================================================================

    @Nested
    @DisplayName("10-minute bucket boundary behaviour")
    class BucketBoundary {

        @Test
        @DisplayName("minute=9 (bucket 10:00) and minute=10 (bucket 10:10) are separated")
        void computeClusters_atExactBoundary_differentBuckets() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "ERROR", "Error at :09", 9),
                    entry("auth-service", "ERROR", "Error at :10", 10)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();
            assertThat(result).hasSize(2);

            // The two bucket starts must be exactly 10 minutes apart
            LocalDateTime min = result.stream().map(LogClusterResult::getTimeBucketStart)
                    .min(LocalDateTime::compareTo).orElseThrow();
            LocalDateTime max = result.stream().map(LogClusterResult::getTimeBucketStart)
                    .max(LocalDateTime::compareTo).orElseThrow();

            assertThat(max).isEqualTo(min.plusMinutes(10));
        }

        @Test
        @DisplayName("minute=0 and minute=9 fall into the same bucket")
        void computeClusters_minute0And9_sameBucket() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "ERROR", "Error 1", 0),
                    entry("auth-service", "ERROR", "Error 2", 9)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("10:59 and 11:00 fall in different buckets (cross-hour boundary)")
        void computeClusters_crossHourBoundary_separateBuckets() {
            LocalDateTime t1 = LocalDateTime.of(2024, 1, 15, 10, 59, 0);
            LocalDateTime t2 = LocalDateTime.of(2024, 1, 15, 11,  0, 0);

            when(logEntryRepository.findAll()).thenReturn(List.of(
                    new LogEntry("auth-service", "ERROR", "Error at 10:59", t1),
                    new LogEntry("auth-service", "ERROR", "Error at 11:00", t2)
            ));

            assertThat(logClusterService.computeClusters()).hasSize(2);
        }
    }

    // =========================================================================
    // MAX_SAMPLES cap
    // =========================================================================

    @Nested
    @DisplayName("sample message capping (MAX_SAMPLES = 3)")
    class SampleMessageCap {

        @Test
        @DisplayName("group of 5 entries: count=5 but only 3 sample messages")
        void computeClusters_fiveEntries_samplesCappedAtThree() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("payment-service", "ERROR", "Timeout txn_001", 1),
                    entry("payment-service", "ERROR", "Timeout txn_002", 2),
                    entry("payment-service", "ERROR", "Timeout txn_003", 3),
                    entry("payment-service", "ERROR", "Timeout txn_004", 4),
                    entry("payment-service", "ERROR", "Timeout txn_005", 5)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(5);
            assertThat(result.get(0).getSampleMessages()).hasSize(3);
        }

        @Test
        @DisplayName("group of 3 entries: all 3 messages included")
        void computeClusters_threeEntries_allSamplesIncluded() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("payment-service", "ERROR", "Error 1", 0),
                    entry("payment-service", "ERROR", "Error 2", 1),
                    entry("payment-service", "ERROR", "Error 3", 2)
            ));

            assertThat(logClusterService.computeClusters().get(0).getSampleMessages()).hasSize(3);
        }
    }

    // =========================================================================
    // Sort order
    // =========================================================================

    @Test
    @DisplayName("clusters are returned in count-descending order")
    void computeClusters_multipleGroups_sortedByCountDescending() {
        // Cluster A: 1 entry (payment ERROR); Cluster B: 3 entries (auth WARN)
        when(logEntryRepository.findAll()).thenReturn(List.of(
                entry("payment-service", "ERROR", "Single error", 0),
                entry("auth-service",    "WARN",  "Warn 1",       1),
                entry("auth-service",    "WARN",  "Warn 2",       2),
                entry("auth-service",    "WARN",  "Warn 3",       3)
        ));

        List<LogClusterResult> result = logClusterService.computeClusters();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCount()).isEqualTo(3);
        assertThat(result.get(1).getCount()).isEqualTo(1);
    }

    // =========================================================================
    // Null / data-quality edge cases
    // =========================================================================

    @Nested
    @DisplayName("null timestamp handling")
    class NullTimestamp {

        @Test
        @DisplayName("entry with null timestamp is grouped (at LocalDateTime.MIN) without NPE")
        void computeClusters_nullTimestamp_doesNotThrow() {
            LogEntry nullTs = new LogEntry("auth-service", "ERROR", "No timestamp", null);
            when(logEntryRepository.findAll()).thenReturn(List.of(nullTs));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTimeBucketStart()).isEqualTo(LocalDateTime.MIN);
        }

        @Test
        @DisplayName("multiple null-timestamp entries from same service+level are grouped together")
        void computeClusters_multipleNullTimestamps_groupedTogether() {
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    new LogEntry("auth-service", "ERROR", "Error A", null),
                    new LogEntry("auth-service", "ERROR", "Error B", null)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(2);
        }
    }

    // =========================================================================
    // Anomaly scoring (z-score frequency spike detection)
    // =========================================================================

    @Nested
    @DisplayName("anomaly scoring (z-score)")
    class AnomalyScoring {

        @Test
        @DisplayName("singleton cluster (no peers) has anomalyScore = 0.0")
        void computeClusters_singleCluster_anomalyScoreIsZero() {
            when(logEntryRepository.findAll()).thenReturn(
                    List.of(entry("payment-service", "ERROR", "Timeout")));

            List<LogClusterResult> result = logClusterService.computeClusters();

            assertThat(result.get(0).getAnomalyScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("peer clusters with equal counts all have anomalyScore = 0.0")
        void computeClusters_equalPeerCounts_allScoresZero() {
            // Two payment-service ERROR clusters in different windows, both count=2
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("payment-service", "ERROR", "Err A1", 1),
                    entry("payment-service", "ERROR", "Err A2", 2),
                    entry("payment-service", "ERROR", "Err B1", 11),
                    entry("payment-service", "ERROR", "Err B2", 12)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            // Two clusters, both count=2, stdDev=0 → all scores = 0.0
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getAnomalyScore() == 0.0);
        }

        @Test
        @DisplayName("spiker cluster has positive score; quiet cluster has negative score")
        void computeClusters_spikingCluster_hasHighPositiveScore() {
            // Cluster A (bucket 10:00): 1 event  — quiet
            // Cluster B (bucket 10:10): 5 events — spike
            // mean=(1+5)/2=3, stdDev=2, z(A)=-1.0, z(B)=+1.0
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "ERROR", "Quiet",   0),   // count=1 in 10:00 bucket
                    entry("auth-service", "ERROR", "Spike 1", 10),  // count=5 in 10:10 bucket
                    entry("auth-service", "ERROR", "Spike 2", 11),
                    entry("auth-service", "ERROR", "Spike 3", 12),
                    entry("auth-service", "ERROR", "Spike 4", 13),
                    entry("auth-service", "ERROR", "Spike 5", 14)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            // result is sorted count-desc: [bucket 10:10 (count=5), bucket 10:00 (count=1)]
            LogClusterResult spike = result.get(0);
            LogClusterResult quiet = result.get(1);

            assertThat(spike.getCount()).isEqualTo(5);
            assertThat(spike.getAnomalyScore()).isGreaterThan(0.0);

            assertThat(quiet.getCount()).isEqualTo(1);
            assertThat(quiet.getAnomalyScore()).isLessThan(0.0);
        }

        @Test
        @DisplayName("scores are computed independently per (serviceName, logLevel) peer group")
        void computeClusters_differentPeerGroups_scoredIndependently() {
            // auth-service ERROR: counts [1, 5] → z-scores will be non-zero
            // payment-service WARN: only 1 cluster → z-score stays 0.0
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service",    "ERROR", "Auth quiet",  0),
                    entry("auth-service",    "ERROR", "Auth spike 1", 10),
                    entry("auth-service",    "ERROR", "Auth spike 2", 11),
                    entry("auth-service",    "ERROR", "Auth spike 3", 12),
                    entry("auth-service",    "ERROR", "Auth spike 4", 13),
                    entry("auth-service",    "ERROR", "Auth spike 5", 14),
                    entry("payment-service", "WARN",  "Payment warn", 0)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            // Find the payment-service WARN singleton cluster
            LogClusterResult paymentWarn = result.stream()
                    .filter(c -> "payment-service".equals(c.getServiceName()) && "WARN".equals(c.getLogLevel()))
                    .findFirst().orElseThrow();

            // Singleton cluster in its own peer group → score must remain 0.0
            assertThat(paymentWarn.getAnomalyScore()).isEqualTo(0.0);

            // auth-service ERROR clusters have 2 peers → at least one non-zero score
            boolean anyNonZeroAuthScore = result.stream()
                    .filter(c -> "auth-service".equals(c.getServiceName()))
                    .anyMatch(c -> c.getAnomalyScore() != 0.0);
            assertThat(anyNonZeroAuthScore).isTrue();
        }

        @Test
        @DisplayName("anomalyScore is rounded to 2 decimal places")
        void computeClusters_scores_roundedToTwoDecimals() {
            // Three clusters with counts [1, 3, 5] → non-trivial z-scores
            when(logEntryRepository.findAll()).thenReturn(List.of(
                    entry("auth-service", "ERROR", "Low 1",  0),
                    entry("auth-service", "ERROR", "Mid 1", 10),
                    entry("auth-service", "ERROR", "Mid 2", 11),
                    entry("auth-service", "ERROR", "Mid 3", 12),
                    entry("auth-service", "ERROR", "Hi 1",  20),
                    entry("auth-service", "ERROR", "Hi 2",  21),
                    entry("auth-service", "ERROR", "Hi 3",  22),
                    entry("auth-service", "ERROR", "Hi 4",  23),
                    entry("auth-service", "ERROR", "Hi 5",  24)
            ));

            List<LogClusterResult> result = logClusterService.computeClusters();

            result.forEach(c -> {
                double score = c.getAnomalyScore();
                double rounded = Math.round(score * 100.0) / 100.0;
                assertThat(score).isEqualTo(rounded);
            });
        }
    }
}
