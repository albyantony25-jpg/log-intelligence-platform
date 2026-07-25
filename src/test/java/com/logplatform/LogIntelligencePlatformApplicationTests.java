package com.logplatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test that verifies the Spring application context loads without errors.
 *
 * @SpringBootTest      – bootstraps the full application context for integration testing.
 * @TestPropertySource  – overrides datasource properties with an in-memory H2 database
 *                        so this test can run in CI without a live PostgreSQL instance.
 *
 * NOTE: To use H2 for tests, add the following dependency to pom.xml (test scope):
 *   <dependency>
 *       <groupId>com.h2database</groupId>
 *       <artifactId>h2</artifactId>
 *       <scope>test</scope>
 *   </dependency>
 *
 * For now the test simply asserts the context loads (no H2 configured by default).
 */
@SpringBootTest
class LogIntelligencePlatformApplicationTests {

    /**
     * Verifies that all beans are wired correctly and the application context
     * starts without throwing exceptions.
     */
    @Test
    void contextLoads() {
        // If the context fails to load, this test will fail with a descriptive error.
    }
}
