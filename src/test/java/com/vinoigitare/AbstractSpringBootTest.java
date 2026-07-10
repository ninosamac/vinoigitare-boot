package com.vinoigitare;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
 *
 * <p>Also points {@code vinoigitare.songs-dir} at a throwaway temp
 * directory, for the exact same reason: a real bug found in testing --
 * {@link com.vinoigitare.storage.TabFileMirror} means {@code
 * SongService.store()} now writes a real {@code .tab} file on every save,
 * so a full-context test that stores a song (e.g. {@code
 * SongUtf8RenderingTest}, which happens to reuse the "Đorđe Đokić -
 * Šašava priča" fixture name) was silently overwriting the real fixture
 * file under {@code ./data/songs} with test content, since only the
 * datasource was isolated here, not the songs directory. Caught by git
 * showing that fixture file as modified after a routine test run.
 */
public abstract class AbstractSpringBootTest {

    @DynamicPropertySource
    static void useIsolatedInMemoryDatabase(DynamicPropertyRegistry registry) {
        String uniqueName = "test-" + UUID.randomUUID();
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + uniqueName + ";DB_CLOSE_DELAY=-1");
    }

    @DynamicPropertySource
    static void useIsolatedSongsDirectory(DynamicPropertyRegistry registry) {
        try {
            var tempDir = Files.createTempDirectory("vinoigitare-test-songs-");
            registry.add("vinoigitare.songs-dir", () -> tempDir.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create isolated test songs directory", e);
        }
    }
}
