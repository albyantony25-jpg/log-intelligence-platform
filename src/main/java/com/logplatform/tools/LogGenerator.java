package com.logplatform.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Standalone log data generator.
 *
 * Purpose:
 *   Generates 50 realistic fake log entries and POSTs each one to the
 *   running Log Intelligence Platform API at http://localhost:8081/logs.
 *
 * How to run (from the project root):
 *   Option A – Maven exec plugin (no extra setup):
 *     mvnw.cmd exec:java -Dexec.mainClass="com.logplatform.tools.LogGenerator"
 *
 *   Option B – After packaging:
 *     java -cp target/log-intelligence-platform-0.0.1-SNAPSHOT.jar \
 *          com.logplatform.tools.LogGenerator
 *
 * Uses only the JDK built-in java.net.http.HttpClient (Java 11+) and
 * Jackson (already on the classpath via spring-boot-starter-web) so no
 * extra dependencies are required.
 *
 * NOT a Spring component — has no @Component / @Service annotation and
 * will never be picked up by @ComponentScan unless explicitly imported.
 */
public class LogGenerator {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private static final String API_URL   = "http://localhost:8081/logs";
    private static final int    LOG_COUNT = 50;

    /** Services that can produce log events. */
    private static final List<String> SERVICES = List.of(
            "auth-service",
            "payment-service",
            "user-service",
            "notification-service"
    );

    /**
     * Log level pool.
     * ERROR appears ~14% of the time (2 out of 14 entries),
     * WARN ~29%, INFO ~57%.
     */
    private static final List<String> LOG_LEVELS = List.of(
            "INFO", "INFO", "INFO", "INFO",
            "INFO", "INFO", "INFO", "INFO",
            "WARN", "WARN", "WARN", "WARN",
            "ERROR", "ERROR"
    );

    /**
     * Realistic log message templates keyed by service → level.
     * {txn}, {user}, {code}, {ms} are substituted with random values at runtime.
     */
    private static final Map<String, Map<String, List<String>>> MESSAGE_TEMPLATES =
            Map.of(
                "auth-service", Map.of(
                    "INFO",  List.of(
                        "User {user} authenticated successfully",
                        "Session token issued for user {user}",
                        "Password reset link sent to user {user}",
                        "OAuth2 token refreshed for user {user}"
                    ),
                    "WARN",  List.of(
                        "Failed login attempt #{code} for user {user}",
                        "Session about to expire for user {user} (idle {ms}ms)",
                        "Suspicious login location detected for user {user}"
                    ),
                    "ERROR", List.of(
                        "JWT signature validation failed for user {user}",
                        "Token blacklist lookup timed out after {ms}ms",
                        "Auth service unable to reach identity provider — code {code}"
                    )
                ),
                "payment-service", Map.of(
                    "INFO",  List.of(
                        "Payment txn_{txn} processed successfully for user {user}",
                        "Refund of ${code} initiated for transaction txn_{txn}",
                        "Payment method verified for user {user}",
                        "Invoice #{txn} generated and dispatched"
                    ),
                    "WARN",  List.of(
                        "Payment txn_{txn} is pending review — fraud score elevated",
                        "Currency conversion rate stale by {ms}ms for txn_{txn}",
                        "Retry #{code} for payment gateway on txn_{txn}"
                    ),
                    "ERROR", List.of(
                        "Payment gateway timeout for transaction txn_{txn} after {ms}ms",
                        "Charge declined for user {user} — insufficient funds (txn_{txn})",
                        "Payment processor returned error code {code} for txn_{txn}",
                        "Duplicate transaction detected: txn_{txn}"
                    )
                ),
                "user-service", Map.of(
                    "INFO",  List.of(
                        "User {user} profile updated successfully",
                        "New account registered: user {user}",
                        "User {user} preferences saved",
                        "Email verified for user {user}"
                    ),
                    "WARN",  List.of(
                        "User {user} has exceeded storage quota by {code}%",
                        "Profile image upload rejected — invalid MIME type for user {user}",
                        "User {user} account flagged for review"
                    ),
                    "ERROR", List.of(
                        "Database write failed for user {user} — constraint violation",
                        "User service cache miss rate exceeded threshold: {ms}ms avg latency",
                        "Failed to delete account for user {user} — foreign key constraint"
                    )
                ),
                "notification-service", Map.of(
                    "INFO",  List.of(
                        "Email notification dispatched to user {user}",
                        "Push notification delivered for event #{code}",
                        "SMS alert sent to user {user} ({ms}ms delivery)",
                        "Notification batch #{txn} queued successfully"
                    ),
                    "WARN",  List.of(
                        "Email bounce rate above threshold for campaign #{code}",
                        "Notification delivery delayed by {ms}ms — queue backpressure",
                        "User {user} has unsubscribed from push notifications"
                    ),
                    "ERROR", List.of(
                        "SMTP connection refused — failed to send email to user {user}",
                        "Push notification provider returned 503 for event #{code}",
                        "Notification worker crashed — restarting (attempt #{code})"
                    )
                )
            );

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        Random     rng    = new Random();
        HttpClient client = HttpClient.newHttpClient();

        // Jackson mapper with Java 8 time support (serialises LocalDateTime)
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        LocalDateTime now = LocalDateTime.now();

        System.out.printf("🚀  Sending %d log entries to %s%n%n", LOG_COUNT, API_URL);

        int successCount = 0;

        for (int i = 1; i <= LOG_COUNT; i++) {

            // --- Pick random attributes ---
            String service  = pick(SERVICES, rng);
            String level    = pick(LOG_LEVELS, rng);
            String message  = buildMessage(service, level, rng);

            // Spread timestamps across the last 2 hours (0–120 minutes ago)
            LocalDateTime timestamp = now.minusMinutes(rng.nextInt(120))
                                         .minusSeconds(rng.nextInt(60));

            // Build the JSON payload as a simple Map (avoids dependency on LogEntry)
            Map<String, Object> payload = Map.of(
                    "serviceName", service,
                    "logLevel",    level,
                    "message",     message,
                    "timestamp",   timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );

            String json = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            // Colour-code output: ✅ 201 Created, ⚠️ anything else
            String icon = response.statusCode() == 201 ? "✅" : "⚠️ ";
            System.out.printf("[%2d/%d] %s  %-24s  %-5s  HTTP %d  %s%n",
                    i, LOG_COUNT, icon,
                    service, level,
                    response.statusCode(),
                    truncate(message, 60));

            if (response.statusCode() == 201) successCount++;

            // Small delay so we don't hammer the API
            Thread.sleep(50);
        }

        System.out.printf("%n✔  Done — %d/%d entries persisted successfully.%n",
                successCount, LOG_COUNT);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Picks a random element from a list using the shared Random instance.
     */
    private static <T> T pick(List<T> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }

    /**
     * Selects a random message template for the given service + level
     * and substitutes placeholder tokens with random values.
     */
    private static String buildMessage(String service, String level, Random rng) {
        List<String> templates = MESSAGE_TEMPLATES
                .getOrDefault(service, Map.of())
                .getOrDefault(level, List.of("Log event occurred"));

        String template = pick(templates, rng);

        return template
                .replace("{txn}",  String.valueOf(1000 + rng.nextInt(9000)))   // e.g. txn_4823
                .replace("{user}", "usr_" + (100 + rng.nextInt(900)))           // e.g. usr_342
                .replace("{code}", String.valueOf(100 + rng.nextInt(900)))      // e.g. 503
                .replace("{ms}",   String.valueOf(200 + rng.nextInt(4800)));    // e.g. 2341ms
    }

    /**
     * Truncates a string to maxLen characters for clean console output.
     */
    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
