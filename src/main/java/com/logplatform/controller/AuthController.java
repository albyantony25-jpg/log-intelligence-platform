package com.logplatform.controller;

import com.logplatform.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Authentication controller — exposes POST /auth/login.
 *
 * This is a demo implementation with a single hardcoded admin account.
 * For production:
 *   - Store users in a database table (Spring Data JPA + UserRepository)
 *   - Use a UserDetailsService to load users by username
 *   - Add role-based access control (@PreAuthorize)
 *
 * Login flow:
 *   1. Client sends  POST /auth/login  { "username": "admin", "password": "..." }
 *   2. Controller verifies credentials against the configured BCrypt hash.
 *   3. On success:  returns { "token": "<JWT>" }
 *   4. On failure:  returns 401 Unauthorized
 *
 * Subsequent requests:
 *   Client includes the token as:  Authorization: Bearer <JWT>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * The admin password stored as a BCrypt hash.
     * Plain-text value: "admin123"   (change before deploying to production!)
     *
     * Generate a new hash:
     *   new BCryptPasswordEncoder().encode("your-password")
     */
    private static final String ADMIN_USERNAME      = "admin";
    private static final String ADMIN_PASSWORD_HASH =
            "$2a$12$xVqYHDfGptMtIKJE4LMm0u2Yb/nVQPq1g0z5I2W9VpFqCR3IuWKlq";

    private final JwtUtil         jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jwtUtil         = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates the user and returns a signed JWT on success.
     *
     * Request body:  { "username": "admin", "password": "admin123" }
     * Success (200): { "token": "<JWT valid for 1 hour>" }
     * Failure (401): { "error": "Invalid credentials" }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> credentials) {

        String username = credentials.get("username");
        String password = credentials.get("password");

        if (ADMIN_USERNAME.equals(username)
                && password != null
                && passwordEncoder.matches(password, ADMIN_PASSWORD_HASH)) {

            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(Map.of("token", token));
        }

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials"));
    }
}
