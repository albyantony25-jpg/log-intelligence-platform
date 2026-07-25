package com.logplatform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler that intercepts exceptions thrown from any controller.
 *
 * @RestControllerAdvice – combines @ControllerAdvice + @ResponseBody.
 *   It lets us define cross-cutting error handling logic in one place instead of
 *   cluttering individual controllers with try/catch blocks.
 *
 * Without this handler, Spring Boot would return a default "Whitelabel Error Page"
 * or a minimal JSON with limited detail when validation fails.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid constraint violations (e.g. @NotBlank on LogEntry fields).
     *
     * MethodArgumentNotValidException is thrown by Spring MVC when the @Valid
     * annotation triggers a validation failure on a @RequestBody parameter.
     *
     * Returns HTTP 400 Bad Request with a structured JSON body listing all
     * violated constraints, e.g.:
     * {
     *   "timestamp": "2024-01-15T10:30:00",
     *   "status": 400,
     *   "errors": ["serviceName must not be blank", "logLevel must not be blank"]
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all human-readable field error messages from the binding result.
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", errors);

        return ResponseEntity.badRequest().body(body);
    }
}
