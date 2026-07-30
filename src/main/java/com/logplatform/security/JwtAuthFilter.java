package com.logplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that extracts and validates the Bearer JWT from every
 * incoming HTTP request.
 *
 * Flow:
 *   1. Read the "Authorization: Bearer <token>" header.
 *   2. Extract and validate the token using JwtUtil.
 *   3. If valid, create a Spring Security Authentication and store it in the
 *      SecurityContext so downstream filters/controllers see an authenticated user.
 *   4. If missing or invalid, do nothing — the SecurityConfig will reject
 *      protected routes with 401 Unauthorized.
 *
 * Extends OncePerRequestFilter to guarantee exactly one execution per request
 * (avoids double-filtering in async dispatch scenarios).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token   = authHeader.substring(BEARER_PREFIX.length());
            String subject = jwtUtil.extractSubject(token);

            if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Token is valid — set authentication in context (no password/roles needed
                // for this demo; extend with UserDetailsService for role-based access).
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(subject, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("JwtAuthFilter: authenticated user '{}'", subject);
            }
        }

        chain.doFilter(request, response);
    }
}
