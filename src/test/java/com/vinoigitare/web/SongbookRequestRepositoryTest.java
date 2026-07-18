package com.vinoigitare.web;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.vinoigitare.AbstractSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link SongbookRequestRepository} against a real (isolated,
 * in-memory) H2 instance -- schema.sql included, same as production --
 * rather than mocking JdbcTemplate, matching {@code
 * DatabaseSongRepositoryTest}'s own convention.
 */
@Tag("io")
@SpringBootTest
class SongbookRequestRepositoryTest extends AbstractSpringBootTest {

    private static final byte[] FAKE_PDF_BYTES = { 0x25, 0x50, 0x44, 0x46, 1, 2, 3 };

    @TempDir
    static Path emptySongsDir;

    @DynamicPropertySource
    static void noFixturesToImport(DynamicPropertyRegistry registry) {
        registry.add("vinoigitare.songs-dir", () -> emptySongsDir.toString());
    }

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clearTable() {
        new JdbcTemplate(dataSource).update("DELETE FROM songbook_request");
    }

    private SongbookRequestRepository newRepository() {
        return new SongbookRequestRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void saveAndFindByIdRoundTripsEveryField() {
        SongbookRequestRepository repository = newRepository();
        SongbookRequest request = SongbookRequest.createNew("[{\"id\":\"1\",\"transpose\":2}]", "My Setlist", true, 3,
                42, FAKE_PDF_BYTES);

        repository.save(request);
        Optional<SongbookRequest> loaded = repository.findById(request.id());

        assertThat(loaded).isPresent();
        SongbookRequest found = loaded.get();
        assertThat(found.id()).isEqualTo(request.id());
        assertThat(found.selection()).isEqualTo(request.selection());
        assertThat(found.bookTitle()).isEqualTo(request.bookTitle());
        assertThat(found.includeChordDiagrams()).isEqualTo(request.includeChordDiagrams());
        assertThat(found.songCount()).isEqualTo(request.songCount());
        assertThat(found.pageCount()).isEqualTo(request.pageCount());
        assertThat(found.amountCents()).isEqualTo(request.amountCents());
        assertThat(found.pdfBytes()).isEqualTo(request.pdfBytes());
        assertThat(found.paid()).isEqualTo(request.paid());
        // Truncated to millis: java.sql.Timestamp round-tripping through
        // H2/JDBC doesn't reliably preserve Instant.now()'s sub-millisecond
        // precision, same reasoning DatabaseSongRepositoryTest sidesteps by
        // not asserting exact createdAt equality at all.
        assertThat(found.createdAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(request.createdAt().truncatedTo(ChronoUnit.MILLIS));
        assertThat(found.paidAt()).isNull();
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        SongbookRequestRepository repository = newRepository();

        assertThat(repository.findById("nonexistent-id")).isEmpty();
    }

    @Test
    void newRequestIsUnpaidWithNoPaidAt() {
        SongbookRequestRepository repository = newRepository();
        SongbookRequest request = SongbookRequest.createNew("[]", null, false, 1, 5, FAKE_PDF_BYTES);

        repository.save(request);
        Optional<SongbookRequest> loaded = repository.findById(request.id());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().paid()).isFalse();
        assertThat(loaded.get().paidAt()).isNull();
    }

    @Test
    void markPaidSetsPaidTrueAndPaidAt() {
        SongbookRequestRepository repository = newRepository();
        SongbookRequest request = SongbookRequest.createNew("[]", null, false, 1, 5, FAKE_PDF_BYTES);
        repository.save(request);
        Instant paidAt = Instant.now();

        repository.markPaid(request.id(), paidAt);
        Optional<SongbookRequest> loaded = repository.findById(request.id());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().paid()).isTrue();
        assertThat(loaded.get().paidAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(paidAt.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void createNewComputesAmountFromPageCountPricingTiers() {
        assertThat(SongbookRequest.createNew("[]", null, true, 1, 10, FAKE_PDF_BYTES).amountCents()).isEqualTo(300);
        assertThat(SongbookRequest.createNew("[]", null, true, 1, 30, FAKE_PDF_BYTES).amountCents()).isEqualTo(500);
        assertThat(SongbookRequest.createNew("[]", null, true, 1, 75, FAKE_PDF_BYTES).amountCents()).isEqualTo(700);
    }
}
