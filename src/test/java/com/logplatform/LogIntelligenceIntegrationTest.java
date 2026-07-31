package com.logplatform;

import com.logplatform.dto.LogClusterResult;
import com.logplatform.model.LogEntry;
import com.logplatform.repository.LogEntryRepository;
import com.logplatform.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class LogIntelligenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:16-alpine"
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // We do not spin up a Redis container here because CacheConfig handles Redis failures gracefully.
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LogEntryRepository logEntryRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken;

    @BeforeEach
    void setup() {
        logEntryRepository.deleteAll();
        validToken = jwtUtil.generateToken("admin");
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void testEndToEndLogIngestionAndClusteringFlow() throws InterruptedException {
        // 1. Ingest Log 1 (ERROR)
        LogEntry entry1 = new LogEntry();
        entry1.setServiceName("checkout-service");
        entry1.setLogLevel("ERROR");
        entry1.setMessage("Payment gateway timeout");
        entry1.setTimestamp(LocalDateTime.of(2024, 1, 1, 10, 5, 0));

        HttpEntity<LogEntry> request1 = new HttpEntity<>(entry1, createHeaders());
        ResponseEntity<Void> response1 = restTemplate.postForEntity("/logs", request1, Void.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 2. Ingest Log 2 (ERROR - same 10 min window)
        LogEntry entry2 = new LogEntry();
        entry2.setServiceName("checkout-service");
        entry2.setLogLevel("ERROR");
        entry2.setMessage("Database lock timeout");
        entry2.setTimestamp(LocalDateTime.of(2024, 1, 1, 10, 6, 0));

        HttpEntity<LogEntry> request2 = new HttpEntity<>(entry2, createHeaders());
        restTemplate.postForEntity("/logs", request2, Void.class);

        // 3. Ingest Log 3 (INFO - should not be clustered)
        LogEntry entry3 = new LogEntry();
        entry3.setServiceName("checkout-service");
        entry3.setLogLevel("INFO");
        entry3.setMessage("Checkout started");
        entry3.setTimestamp(LocalDateTime.of(2024, 1, 1, 10, 7, 0));

        HttpEntity<LogEntry> request3 = new HttpEntity<>(entry3, createHeaders());
        restTemplate.postForEntity("/logs", request3, Void.class);

        // Wait for async ingestion to complete (LogIngestionService uses @Async)
        long start = System.currentTimeMillis();
        while (logEntryRepository.count() < 3 && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(100);
        }
        assertThat(logEntryRepository.count()).isEqualTo(3);

        // 4. Query Clusters
        HttpEntity<Void> getRequest = new HttpEntity<>(createHeaders());
        ResponseEntity<List<LogClusterResult>> clusterResponse = restTemplate.exchange(
                "/logs/clusters",
                HttpMethod.GET,
                getRequest,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(clusterResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<LogClusterResult> clusters = clusterResponse.getBody();
        assertThat(clusters).isNotNull();
        assertThat(clusters).hasSize(1); // The INFO log is excluded

        LogClusterResult cluster = clusters.get(0);
        assertThat(cluster.getServiceName()).isEqualTo("checkout-service");
        assertThat(cluster.getLogLevel()).isEqualTo("ERROR");
        assertThat(cluster.getCount()).isEqualTo(2);
        assertThat(cluster.getSampleMessages()).contains("Payment gateway timeout", "Database lock timeout");
    }

    @Test
    void testUnauthenticatedAccessIsBlocked() {
        LogEntry entry = new LogEntry();
        entry.setServiceName("test-service");
        entry.setLogLevel("INFO");
        entry.setMessage("Test");

        // No Authorization header
        ResponseEntity<Void> response = restTemplate.postForEntity("/logs", entry, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); // Spring Security returns 403 or 401 without auth
    }
}
