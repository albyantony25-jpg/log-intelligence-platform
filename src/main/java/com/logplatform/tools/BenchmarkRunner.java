package com.logplatform.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates a synthetic dataset of 10,000+ log entries and benchmarks API performance.
 */
public class BenchmarkRunner {

    private static final String API_BASE = "http://localhost:8081";
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();

    private static final List<String> SERVICES = List.of("auth-service", "payment-service", "inventory-service", "frontend-app");
    private static final List<String> LEVELS = List.of("INFO", "INFO", "INFO", "WARN", "ERROR"); // 60% INFO, 20% WARN, 20% ERROR
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Benchmark Runner...");
        
        // 1. Login to get token
        String token = login();
        if (token == null) {
            System.err.println("Login failed. Is the server running at " + API_BASE + "?");
            return;
        }
        System.out.println("Successfully authenticated.");

        // 2. Benchmark Ingestion (10,000 requests)
        int totalRequests = 10000;
        System.out.println("Generating and ingesting " + totalRequests + " log entries (async HTTP calls)...");
        
        long ingestStart = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(50); // Send concurrent requests
        
        CompletableFuture<?>[] futures = new CompletableFuture[totalRequests];
        for (int i = 0; i < totalRequests; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    String service = SERVICES.get(random.nextInt(SERVICES.size()));
                    String level = LEVELS.get(random.nextInt(LEVELS.size()));
                    String message = "Synthetic benchmark message " + random.nextInt(10000);
                    // Scatter over the last 60 minutes
                    LocalDateTime timestamp = LocalDateTime.now().minusMinutes(random.nextInt(60));
                    
                    Map<String, Object> logEntry = Map.of(
                            "serviceName", service,
                            "logLevel", level,
                            "message", message,
                            "timestamp", timestamp.format(DateTimeFormatter.ISO_DATE_TIME)
                    );
                    
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(API_BASE + "/logs"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(logEntry)))
                            .build();
                            
                    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() != 202) {
                        System.err.println("Unexpected status: " + response.statusCode());
                    }
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        long ingestEnd = System.currentTimeMillis();
        
        System.out.println("Ingestion completed in " + (ingestEnd - ingestStart) + " ms");
        System.out.println("Ingestion throughput: " + (totalRequests * 1000L / Math.max(1, ingestEnd - ingestStart)) + " req/sec");

        // Wait a moment for background processing to finish (since ingestion API is async)
        System.out.println("Waiting 5 seconds for async DB writes to catch up...");
        Thread.sleep(5000);

        // 3. Benchmark Query: GET /logs (Pagination)
        System.out.println("Benchmarking query: GET /logs?page=0&size=100");
        long queryStart = System.currentTimeMillis();
        HttpRequest queryRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/logs?page=0&size=100"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        client.send(queryRequest, HttpResponse.BodyHandlers.discarding());
        long queryEnd = System.currentTimeMillis();
        System.out.println("Paginated Query time: " + (queryEnd - queryStart) + " ms");

        // 4. Benchmark Query: GET /logs/clusters
        System.out.println("Benchmarking query: GET /logs/clusters");
        long clusterStart = System.currentTimeMillis();
        HttpRequest clusterRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/logs/clusters"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        client.send(clusterRequest, HttpResponse.BodyHandlers.discarding());
        long clusterEnd = System.currentTimeMillis();
        System.out.println("Clustering Query time: " + (clusterEnd - clusterStart) + " ms");
        
        executor.shutdown();
    }
    
    private static String login() {
        try {
            Map<String, String> creds = Map.of("username", "admin", "password", "password");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(creds)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<?, ?> map = mapper.readValue(response.body(), Map.class);
                return (String) map.get("token");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
