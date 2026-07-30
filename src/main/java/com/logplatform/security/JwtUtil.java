package com.logplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility for creating, signing, and validating JSON Web Tokens.
 *
 * Algorithm: HMAC-SHA256 (HS256) — symmetric; the same secret key is used
 * to both sign and verify tokens.  For production, consider RS256 (asymmetric)
 * so the public key can be distributed to downstream services without exposing
 * the signing secret.
 *
 * Configuration:
 *   jwt.secret  – must be at least 32 ASCII characters (256 bits for HS256)
 *   jwt.expiry-ms – token lifetime in milliseconds (default 1 hour)
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey signingKey;
    private final long      expiryMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiry-ms:3600000}") long expiryMs) {
        // Keys.hmacShaKeyFor requires a key of at least 256 bits for HS256
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs   = expiryMs;
    }

    /**
     * Generates a signed JWT for the given subject (username).
     *
     * @param subject username or user identifier
     * @return compact, URL-safe JWT string  (header.payload.signature)
     */
    public String generateToken(String subject) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extracts and validates the subject (username) from a JWT.
     *
     * @param token the compact JWT string
     * @return the subject, or null if the token is invalid or expired
     */
    public String extractSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JwtUtil: invalid token — {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Returns true if the token parses successfully and is not expired.
     */
    public boolean isValid(String token) {
        return extractSubject(token) != null;
    }
}
