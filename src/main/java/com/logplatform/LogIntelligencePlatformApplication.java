package com.logplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Log Intelligence Platform.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   - @Configuration      – marks this class as a source of bean definitions
 *   - @EnableAutoConfiguration – tells Spring Boot to auto-configure beans
 *     based on the classpath (e.g. DataSource, EntityManager, DispatcherServlet)
 *   - @ComponentScan      – scans all sub-packages for @Component / @Service /
 *     @Repository / @Controller classes
 */
@SpringBootApplication
@EnableAsync   // Activates @Async support for parallel AI summary calls
public class LogIntelligencePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogIntelligencePlatformApplication.class, args);
    }
}
