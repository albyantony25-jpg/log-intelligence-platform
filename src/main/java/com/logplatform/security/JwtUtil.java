package com.logplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utility for generating and validating JSON Web Tokens (JWTs).
 */
@Component
public class JwtUtil {

    // 256-bit secret key for signing tokens. In production, load via environment variable.
    private static final String SECRET = "log-intelligence-platform-super-secret-key-2024!";
    private final SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes());

    // Tokens are valid for 24 hours
    private static final long EXPIRATION_TIME_MS = 86400000L;

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return !extractClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false; // Token is expired or tampered with
        }
    }
}
