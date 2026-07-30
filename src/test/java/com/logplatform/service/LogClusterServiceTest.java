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
}
