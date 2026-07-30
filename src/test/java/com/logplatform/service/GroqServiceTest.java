package com.logplatform.service;

import com.logplatform.dto.LogClusterResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GroqService}.
 *
 * Testing challenge:
 *   GroqService uses a JDK {@link HttpClient} created in a field initializer and
 *   an API key injected via @Value — neither is visible to a plain 'new' call.
 *
 * Solution:
 *   1. Instantiate GroqService directly with 'new GroqService()'.
 *   2. Use {@link ReflectionTestUtils#setField} to replace the package-visible
 *      'httpClient' field with a Mockito mock and to set 'apiKey' to a test value.
 *      (The field was made non-final to allow this — a standard test-enablement pattern.)
 *   3. Mock HttpResponse<String> to return controlled status codes and bodies.
 *
 * This gives us full control over every code path without a Spring context or
 * a real network connection.
 *
 * Coverage targets:
 *   - Blank / null API key → immediate fallback (no HTTP call)
 *   - 200 OK with valid JSON → extracted content returned
 *   - HTTP non-200 (401, 429, 500) → fallback
 *   - IOException / InterruptedException thrown by HttpClient → fallback
 *   - Malformed JSON response → fallback
 *   - Missing choices path in JSON → fallback
 *   - Null content field in JSON → fallback
 *   - Response content with trailing whitespace → trimmed
 */
@ExtendWith(MockitoExtension.class)
// LENIENT: @BeforeEach stubs the HTTP mock for the happy-path default, but some
// nested tests (blank key, transport failures) short-circuit before the mock is
// invoked. Strict mode would flag those stubs as UnnecessaryStubbings.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroqService")
class GroqServiceTest {

    // The real service under test — we construct it directly (no Spring context)
    private GroqService groqService;

    // Mockito mock for the JDK HttpClient
    @Mock
    private HttpClient mockHttpClient;

    // Mockito mock for the HTTP response — unchecked cast needed for raw generic type
    @SuppressWarnings("unchecked")
    @Mock
    private HttpResponse<String> mockResponse;

    // A minimal LogClusterResult used as input across all tests
    private LogClusterResult sampleCluster;

    // A valid Groq API response JSON — matches the expected structure:
    // { "choices": [ { "message": { "content": "..." } } ] }
    private static final String VALID_GROQ_RESPONSE = """
            {
              "choices": [
                {
                  "message": {
                    "content": "High error rate on payment-service — likely a gateway timeout."
                  }
                }
              ]
            }
            """;

    private static final String EXPECTED_SUMMARY =
            "High error rate on payment-service — likely a gateway timeout.";

    private static final String FALLBACK = "Summary unavailable";

    @BeforeEach
    void setUp() throws Exception {
        // Construct the real service (no Spring context needed)
        groqService = new GroqService();

        // Inject a valid API key and the mock HttpClient via reflection.
        // ReflectionTestUtils.setField() bypasses access modifiers, which is
        // safe and common in unit tests for Spring applications.
        ReflectionTestUtils.setField(groqService, "apiKey", "test-key-abc123");
        ReflectionTestUtils.setField(groqService, "httpClient", mockHttpClient);

        // Default stub: HTTP 200 with valid JSON.
        // Marked lenient because blank-key and transport-failure tests
        // short-circuit before invoking the HTTP client.
        lenient().doReturn(mockResponse)
                .when(mockHttpClient)
                .send(any(HttpRequest.class), any());
        lenient().when(mockResponse.statusCode()).thenReturn(200);
        lenient().when(mockResponse.body()).thenReturn(VALID_GROQ_RESPONSE);

        // Build a reusable input cluster
        sampleCluster = new LogClusterResult(
                "payment-service",
                "ERROR",
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                5,
                List.of("Gateway timeout on txn_001",
                        "Gateway timeout on txn_002",
                        "Gateway timeout on txn_003")
        );
    }

    // =========================================================================
    // Blank / null API key — should never reach the network
    // =========================================================================

    @Nested
    @DisplayName("when API key is missing")
    class WhenApiKeyMissing {

        @Test
        @DisplayName("blank API key returns fallback without making an HTTP call")
        void summarize_blankApiKey_returnsFallback() throws Exception {
            ReflectionTestUtils.setField(groqService, "apiKey", "");

            String result = groqService.summarize(sampleCluster);

            assertThat(result).isEqualTo(FALLBACK);
            // The HTTP client must NOT have been called
            verify(mockHttpClient, never()).send(any(), any());
        }

        @Test
        @DisplayName("whitespace-only API key returns fallback without HTTP call")
        void summarize_whitespaceApiKey_returnsFallback() throws Exception {
            ReflectionTestUtils.setField(groqService, "apiKey", "   ");

            String result = groqService.summarize(sampleCluster);

            assertThat(result).isEqualTo(FALLBACK);
            verify(mockHttpClient, never()).send(any(), any());
        }

        @Test
        @DisplayName("null API key returns fallback without HTTP call")
        void summarize_nullApiKey_returnsFallback() throws Exception {
            ReflectionTestUtils.setField(groqService, "apiKey", null);

            String result = groqService.summarize(sampleCluster);

            assertThat(result).isEqualTo(FALLBACK);
            verify(mockHttpClient, never()).send(any(), any());
        }
    }

    // =========================================================================
    // Happy path — valid API key + valid response
    // =========================================================================

    @Nested
    @DisplayName("when API responds successfully")
    class WhenSuccess {

        @Test
        @DisplayName("HTTP 200 with valid JSON returns the extracted content string")
        void summarize_http200ValidJson_returnsContent() {
            String result = groqService.summarize(sampleCluster);

            assertThat(result).isEqualTo(EXPECTED_SUMMARY);
        }

        @Test
        @DisplayName("returned content is trimmed of surrounding whitespace")
        void summarize_contentWithWhitespace_returnsTrimmed() {
            String jsonWithPadding = """
                    {
                      "choices": [
                        { "message": { "content": "  Some summary with spaces.  " } }
                      ]
                    }
                    """;
            when(mockResponse.body()).thenReturn(jsonWithPadding);

            String result = groqService.summarize(sampleCluster);

            assertThat(result).isEqualTo("Some summary with spaces.");
        }

        @Test
        @DisplayName("exactly one HTTP POST is made per summarize() call")
        void summarize_validKey_exactlyOneHttpCall() throws Exception {
            groqService.summarize(sampleCluster);

            verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any());
        }
    }

    // =========================================================================
    // HTTP error responses — non-200 status codes
    // =========================================================================

    @Nested
    @DisplayName("when API returns an error HTTP status")
    class WhenHttpError {

        @Test
        @DisplayName("HTTP 401 Unauthorized returns fallback")
        void summarize_http401_returnsFallback() {
            when(mockResponse.statusCode()).thenReturn(401);
            when(mockResponse.body()).thenReturn("{\"error\":\"invalid_api_key\"}");

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("HTTP 429 Rate Limited returns fallback")
        void summarize_http429_returnsFallback() {
            when(mockResponse.statusCode()).thenReturn(429);
            when(mockResponse.body()).thenReturn("{\"error\":\"rate_limit_exceeded\"}");

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("HTTP 500 Internal Server Error returns fallback")
        void summarize_http500_returnsFallback() {
            when(mockResponse.statusCode()).thenReturn(500);
            when(mockResponse.body()).thenReturn("Internal Server Error");

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }
    }

    // =========================================================================
    // Network / transport failures
    // =========================================================================

    @Nested
    @DisplayName("when the HTTP transport fails")
    class WhenTransportFailure {

        @Test
        @DisplayName("IOException (connection refused / timeout) returns fallback")
        void summarize_ioException_returnsFallback() throws Exception {
            doThrow(new IOException("Connection refused"))
                    .when(mockHttpClient).send(any(), any());

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("InterruptedException (request cancelled / thread interrupted) returns fallback")
        void summarize_interruptedException_returnsFallback() throws Exception {
            doThrow(new InterruptedException("Request interrupted"))
                    .when(mockHttpClient).send(any(), any());

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }
    }

    // =========================================================================
    // Malformed or unexpected JSON responses
    // =========================================================================

    @Nested
    @DisplayName("when the API response JSON is unexpected")
    class WhenBadResponseJson {

        @Test
        @DisplayName("completely malformed JSON returns fallback")
        void summarize_malformedJson_returnsFallback() {
            when(mockResponse.body()).thenReturn("not-json-at-all {{{");

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("JSON with empty choices array returns fallback")
        void summarize_emptyChoicesArray_returnsFallback() {
            when(mockResponse.body()).thenReturn("{\"choices\": []}");

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("JSON with missing 'choices' key returns fallback")
        void summarize_missingChoicesKey_returnsFallback() {
            when(mockResponse.body()).thenReturn("{\"model\": \"llama-3.3-70b-versatile\"}");

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("JSON where 'content' is null returns fallback")
        void summarize_nullContent_returnsFallback() {
            String jsonNullContent = """
                    {
                      "choices": [
                        { "message": { "content": null } }
                      ]
                    }
                    """;
            when(mockResponse.body()).thenReturn(jsonNullContent);

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("JSON where 'message' key is absent returns fallback")
        void summarize_missingMessageKey_returnsFallback() {
            String jsonNoMessage = """
                    {
                      "choices": [
                        { "finish_reason": "stop" }
                      ]
                    }
                    """;
            when(mockResponse.body()).thenReturn(jsonNoMessage);

            assertThat(groqService.summarize(sampleCluster)).isEqualTo(FALLBACK);
        }
    }
}
