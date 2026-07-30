package com.logplatform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the Log Intelligence Platform.
 *
 * Access rules:
 *   POST /auth/login  – public (returns JWT on valid credentials)
 *   GET  /actuator/** – public (health-check used by Docker / load-balancers)
 *   ALL  other routes – require a valid Bearer JWT
 *
 * Session policy: STATELESS — no HttpSession is created or used.  Every
 * request must carry a JWT; there is no cookie-based session.
 *
 * CSRF is disabled because:
 *   a) The API is consumed by non-browser clients (React SPA with fetch, curl).
 *   b) JWT-in-Authorization-header is not vulnerable to CSRF (browsers never
 *      automatically attach custom headers on cross-site requests).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — safe for stateless JWT API (see Javadoc above)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session — no server-side session storage
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Route-level access rules
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/login").permitAll()      // login is public
                    .requestMatchers("/actuator/**").permitAll()     // health checks
                    .anyRequest().authenticated()                    // everything else needs JWT
            )

            // Insert JwtAuthFilter before Spring's default username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder — used to verify the admin password in AuthController.
     * BCrypt is intentionally slow (cost factor 12 by default) to resist brute-force.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
