package com.logplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.config.CacheConfig;
import com.logplatform.config.GroqRateLimiter;
import com.logplatform.dto.LogClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Client service for the Groq Chat Completions API.
 *
 * Single responsibility: given a {@link LogClusterResult}, build a prompt,
 * send it to Groq's LLM, and return a concise plain-English summary.
 *
 * Configuration (application.properties):
 *   groq.api.key=${GROQ_API_KEY:}
 *
 * The value is sourced from the GROQ_API_KEY environment variable at runtime.
 * If the variable is unset or the API call fails for any reason, the method
 * returns "Summary unavailable" rather than propagating the error to callers.
 *
 * HTTP transport: JDK built-in java.net.http.HttpClient (Java 11+).
 * JSON parsing:   Jackson ObjectMapper (already on classpath via spring-boot-starter-web).
 * No extra Maven dependencies are required.
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    /** Groq Chat Completions endpoint. */
    private static final String GROQ_API_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    /**
     * Model to use.  llama-3.3-70b-versatile is Groq's recommended general-purpose
     * model — fast, capable, and available on the free tier.
     */
    private static final String MODEL = "llama-3.3-70b-versatile";

    /** Timeout for the Groq HTTP call. Keeps the endpoint responsive under load. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /** Returned when the AI call fails for any reason. */
    private static final String FALLBACK_SUMMARY = "Summary unavailable";

    /**
     * Injected from application.properties:  groq.api.key=${GROQ_API_KEY:}
     *
     * The :  (empty default) means Spring won't fail to start if GROQ_API_KEY
     * is unset — the key will just be an empty string, and summarize() will
     * short-circuit with the fallback message.
     */
    @Value("${groq.api.key:}")
    private String apiKey;

    /** Token-bucket rate limiter — prevents exhausting the Groq API quota. */
    private final GroqRateLimiter rateLimiter;

    /** Reused across requests — HttpClient is thread-safe and connection-pool-aware. */
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Reused across requests — ObjectMapper is thread-safe after configuration. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroqService(GroqRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates a 1-2 sentence AI explanation of the given log cluster.
     *
     * Caching:
     *   @Cacheable stores the result in Redis under a key combining serviceName,
     *   logLevel, and timeBucketStart.  On a cache hit, the HTTP call to Groq is
     *   skipped entirely, reducing latency and API rate-limit usage.
     *
     *   The 'unless' condition prevents caching the fallback string — if Groq
     *   failed transiently, we want to retry on the next request rather than
     *   permanently caching "Summary unavailable" for 1 hour.
     *
     *   TTL is configured to 1 hour in CacheConfig.  Cache errors (Redis down)
     *   are handled by CacheConfig.errorHandler() — they are logged and ignored,
     *   so the method always runs normally even when Redis is unavailable.
     *
     * @param cluster The log cluster to summarise.
     * @return Plain-English AI summary, or "Summary unavailable" on error.
     */
    @Cacheable(
            value  = CacheConfig.GROQ_CACHE,
            key    = "#cluster.serviceName + ':' + #cluster.logLevel + ':' + #cluster.timeBucketStart",
            unless = "#result == 'Summary unavailable'"
    )
    public String summarize(LogClusterResult cluster) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GroqService: GROQ_API_KEY is not set — returning fallback summary.");
            return FALLBACK_SUMMARY;
        }

        // Rate-limit check: non-blocking, returns false if quota exhausted
        if (!rateLimiter.tryConsume()) {
            log.warn("GroqService: rate limit reached ({} req/min) — returning fallback for [{}/{}]",
                    rateLimiter.getRequestsPerMinute(),
                    cluster.getServiceName(), cluster.getLogLevel());
            return FALLBACK_SUMMARY;
        }

        try {
            String prompt  = buildPrompt(cluster);
            String payload = buildRequestPayload(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_API_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("GroqService: API returned HTTP {} — body: {}",
                        response.statusCode(), truncate(response.body(), 200));
                return FALLBACK_SUMMARY;
            }

            return parseContent(response.body());

        } catch (Exception ex) {
            // Catch-all: InterruptedException, IOException, JsonProcessingException, etc.
            log.warn("GroqService: Failed to get AI summary for cluster [{} / {}]: {}",
                    cluster.getServiceName(), cluster.getLogLevel(), ex.getMessage());
            return FALLBACK_SUMMARY;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the SRE-assistant prompt with structured cluster details embedded.
     *
     * The prompt instructs the model to focus on:
     *   - Likely cause (pattern in the sample messages)
     *   - Urgency (derived from log level and count)
     *   - Conciseness (1-2 sentences max)
     */
    private static String buildPrompt(LogClusterResult cluster) {
        String samples = cluster.getSampleMessages().stream()
                .map(m -> "  - " + m)
                .collect(Collectors.joining("\n"));

        return String.format(
                "You are an SRE assistant. Explain this log cluster in 1-2 plain English " +
                "sentences, focusing on likely cause and urgency.\n\n" +
                "Cluster details:\n" +
                "  Service:       %s\n" +
                "  Log level:     %s\n" +
                "  Time window:   %s\n" +
                "  Event count:   %d\n" +
                "  Sample messages:\n%s",
                cluster.getServiceName(),
                cluster.getLogLevel(),
                cluster.getTimeBucketStart(),
                cluster.getCount(),
                samples
        );
    }

    /**
     * Serialises the Groq Chat Completions request payload as JSON.
     *
     * We build the JSON manually as a formatted string to avoid needing a
     * request-specific POJO class.  Jackson's writeValueAsString is used only
     * to properly escape the prompt (handles newlines, quotes, Unicode, etc.)
     * so there is no risk of JSON injection.
     *
     * @throws com.fasterxml.jackson.core.JsonProcessingException if escaping fails (never in practice)
     */
    private String buildRequestPayload(String prompt) throws Exception {
        // Safely escape the prompt string before embedding it in JSON
        String escapedPrompt = objectMapper.writeValueAsString(prompt);

        return String.format(
                "{" +
                "  \"model\": \"%s\"," +
                "  \"messages\": [" +
                "    { \"role\": \"user\", \"content\": %s }" +
                "  ]," +
                "  \"temperature\": 0.4," +
                "  \"max_tokens\": 150" +
                "}",
                MODEL,
                escapedPrompt   // already a valid JSON string literal with surrounding quotes
        );
    }

    /**
     * Extracts the assistant's reply text from the Groq response JSON.
     *
     * Expected structure:
     * {
     *   "choices": [
     *     { "message": { "content": "..." } }
     *   ]
     * }
     *
     * @throws Exception if the JSON is malformed or the expected path is absent.
     */
    private String parseContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode content = root
                .path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (content.isMissingNode() || content.isNull()) {
            log.warn("GroqService: Unexpected response shape — 'choices[0].message.content' missing.");
            return FALLBACK_SUMMARY;
        }

        return content.asText().trim();
    }

    /** Trims a string for safe log output. */
    private static String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : s;
    }
}
