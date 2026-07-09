package com.vinoigitare;

import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for every {@code @SpringBootTest}: points the datasource at an
 * isolated, uniquely-named in-memory H2 database instead of the real
 * file-based one configured in {@code application.yml}
 * ({@code ./data/vinoigitare-db}), which is production data and shouldn't
 * be touched, locked, or polluted by test runs.
 *
 * <p>{@code schema.sql} still runs against whatever datasource is
 * configured (that's unconditional, via {@code spring.sql.init.mode
 * =always}), so every test gets a freshly-created {@code song} table.
 * {@code DB_CLOSE_DELAY=-1} keeps the in-memory database alive for the
 * lifetime of the Spring context rather than disappearing the instant the
 * last connection closes.
 */
public abstract class AbstractSpringBootTest {

    @DynamicPropertySource
    static void useIsolatedInMemoryDatabase(DynamicPropertyRegistry registry) {
        String uniqueName = "test-" + UUID.randomUUID();
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + uniqueName + ";DB_CLOSE_DELAY=-1");
    }
}
