package com.vinoigitare.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.vinoigitare.AbstractSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link LogAnalyticsRepository} against a real (isolated,
 * in-memory) H2 instance -- schema.sql included, same as production --
 * matching {@code DatabaseSongRepositoryTest}'s existing convention.
 */
@Tag("io")
@SpringBootTest
class LogAnalyticsRepositoryTest extends AbstractSpringBootTest {

    @Autowired
    private LogAnalyticsRepository repository;

    @Autowired
    private DataSource dataSource;

    // The Spring context (and therefore this in-memory database) is shared
    // across every @Test method in this class -- see
    // DatabaseSongRepositoryTest's own comment on this.
    @BeforeEach
    void clearAnalyticsTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM daily_page_hit");
        jdbcTemplate.update("DELETE FROM daily_referrer_hit");
        jdbcTemplate.update("DELETE FROM log_analytics_state");
    }

    @Test
    void lastProcessedAtDefaultsToEpochWhenNeverSaved() {
        assertThat(repository.lastProcessedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void lastProcessedAtRoundTripsAndCanBeUpdatedAgain() {
        Instant first = Instant.parse("2026-07-20T10:00:00Z");
        repository.saveLastProcessedAt(first);
        assertThat(repository.lastProcessedAt()).isEqualTo(first);

        Instant second = Instant.parse("2026-07-21T11:30:00Z");
        repository.saveLastProcessedAt(second);
        assertThat(repository.lastProcessedAt()).isEqualTo(second);
    }

    @Test
    void incrementPageHitsInsertsThenAccumulatesOnTheSameDateAndPath() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        repository.incrementPageHits(date, "/akordi/1/some-song", 3);
        repository.incrementPageHits(date, "/akordi/1/some-song", 2);
        repository.incrementPageHits(date, "/", 1);

        assertThat(repository.totalHitsSince(date)).isEqualTo(6);
        assertThat(repository.topPathsSince(date, 10)).containsExactly(
                new LogAnalyticsRepository.PathHitTotal("/akordi/1/some-song", 5),
                new LogAnalyticsRepository.PathHitTotal("/", 1));
    }

    @Test
    void totalHitsSinceExcludesHitsBeforeTheGivenDate() {
        repository.incrementPageHits(LocalDate.of(2026, 7, 1), "/old-page", 100);
        repository.incrementPageHits(LocalDate.of(2026, 7, 20), "/recent-page", 4);

        assertThat(repository.totalHitsSince(LocalDate.of(2026, 7, 15))).isEqualTo(4);
    }

    @Test
    void incrementReferrerHitsInsertsThenAccumulatesOnTheSameDateAndHost() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        repository.incrementReferrerHits(date, "google.com", 5);
        repository.incrementReferrerHits(date, "google.com", 5);
        repository.incrementReferrerHits(date, LogAnalyticsRepository.DIRECT_REFERRER, 1);

        assertThat(repository.topReferrersSince(date, 10)).containsExactly(
                new LogAnalyticsRepository.ReferrerHitTotal("google.com", 10),
                new LogAnalyticsRepository.ReferrerHitTotal(LogAnalyticsRepository.DIRECT_REFERRER, 1));
    }

    @Test
    void topPathsSinceRespectsTheLimit() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        repository.incrementPageHits(date, "/a", 3);
        repository.incrementPageHits(date, "/b", 2);
        repository.incrementPageHits(date, "/c", 1);

        assertThat(repository.topPathsSince(date, 2)).hasSize(2);
    }

    @Test
    void totalHitsSinceReturnsZeroWhenNothingRecorded() {
        assertThat(repository.totalHitsSince(LocalDate.of(2026, 7, 20))).isZero();
    }

    // topPathsSince/topReferrersSince returning empty lists rather than
    // throwing/null when nothing's recorded is exercised implicitly by
    // AdminControllerTest's own default-stub setup depending on that
    // shape -- explicit here too since it's cheap and this is the class
    // that actually defines the behavior.
    @Test
    void topPathsSinceReturnsEmptyListWhenNothingRecorded() {
        assertThat(repository.topPathsSince(LocalDate.of(2026, 7, 20), 10)).isEqualTo(List.of());
    }
}
