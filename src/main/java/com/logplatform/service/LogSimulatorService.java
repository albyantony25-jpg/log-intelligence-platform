package com.logplatform.service;

import com.logplatform.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class LogSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(LogSimulatorService.class);

    private final LogIngestionService logIngestionService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> simulationTask;

    private final Random random = new Random();

    // Realistic scenarios mapped to services
    private final List<Map<String, String>> scenarios = List.of(
            Map.of("service", "payment-service", "level", "ERROR", "message", "Database connection timeout (5000ms) on transactions DB"),
            Map.of("service", "auth-service", "level", "ERROR", "message", "NullPointerException during token validation"),
            Map.of("service", "api-gateway", "level", "WARN", "message", "Rate limit exceeded for client IP 192.168.1.45"),
            Map.of("service", "inventory-service", "level", "WARN", "message", "Memory usage spiked above 85%"),
            Map.of("service", "checkout-service", "level", "ERROR", "message", "Upstream payment gateway unreachable (HTTP 503)"),
            Map.of("service", "payment-service", "level", "ERROR", "message", "Deadlock detected in transactions DB"),
            Map.of("service", "auth-service", "level", "WARN", "message", "Invalid login attempt from unknown IP"),
            Map.of("service", "inventory-service", "level", "ERROR", "message", "Failed to sync stock with warehouse system")
    );

    public LogSimulatorService(LogIngestionService logIngestionService) {
        this.logIngestionService = logIngestionService;
    }

    public synchronized void startSimulation() {
        if (simulationTask != null && !simulationTask.isDone()) {
            log.info("Simulation is already running.");
            return;
        }

        log.info("Starting log simulation...");
        scheduleNextLog();
    }

    public synchronized void stopSimulation() {
        if (simulationTask != null) {
            simulationTask.cancel(false);
            log.info("Log simulation stopped.");
        }
    }

    public synchronized boolean isRunning() {
        return simulationTask != null && !simulationTask.isCancelled() && !simulationTask.isDone();
    }

    private void scheduleNextLog() {
        // Randomize interval between 2 and 8 seconds
        int delaySeconds = 2 + random.nextInt(7);
        
        simulationTask = scheduler.schedule(() -> {
            generateLog();
            scheduleNextLog(); // Schedule the next one recursively
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void generateLog() {
        Map<String, String> scenario = scenarios.get(random.nextInt(scenarios.size()));
        
        LogEntry entry = new LogEntry(
                scenario.get("service"),
                scenario.get("level"),
                scenario.get("message"),
                LocalDateTime.now()
        );

        // Feed directly into the async ingestion pipeline
        logIngestionService.ingestLog(entry);
        
        log.info("Simulated log injected: [{}] {} - {}", entry.getServiceName(), entry.getLogLevel(), entry.getMessage());
    }
}
