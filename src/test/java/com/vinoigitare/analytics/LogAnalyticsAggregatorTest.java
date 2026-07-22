package com.vinoigitare.analytics;

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
 * Exercises {@link LogAnalyticsAggregator#aggregate(List)} against real
 * fixture log lines built to match {@code RequestLoggingFilter}'s exact
 * message format (see that class's Javadoc on why the two are a
 * contract), and a real (isolated, in-memory) H2 instance via {@link
 * LogAnalyticsRepository} -- matching {@code DatabaseSongRepositoryTest}'s
 * convention, since the SQL upserts are as much under test here as the
 * parsing/aggregation logic.
 */
@Tag("io")
@SpringBootTest
class LogAnalyticsAggregatorTest extends AbstractSpringBootTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 20);

    @Autowired
    private LogAnalyticsAggregator aggregator;

    @Autowired
    private LogAnalyticsRepository repository;

    @Autowired
    private DataSource dataSource;

    // Shared context/database across every @Test method in this class --
    // see DatabaseSongRepositoryTest's own comment on this. Also resets
    // the aggregator's cursor, since a couple of tests below depend on
    // starting from Instant.EPOCH.
    @BeforeEach
    void clearAnalyticsTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM daily_page_hit");
        jdbcTemplate.update("DELETE FROM daily_referrer_hit");
        jdbcTemplate.update("DELETE FROM log_analytics_state");
    }

    @Test
    void countsRealRequestsAndGroupsByDayAndPath() {
        aggregator.aggregate(List.of(
                requestLine("2026-07-20T10:00:00.000Z", "/", "-", "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T11:00:00.000Z", "/", "-", "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T12:00:00.000Z", "/akordi/1/some-song", "-", "Mozilla/5.0 Real Browser")));

        assertThat(repository.totalHitsSince(DAY)).isEqualTo(3);
        assertThat(repository.topPathsSince(DAY, 10)).containsExactly(
                new LogAnalyticsRepository.PathHitTotal("/", 2),
                new LogAnalyticsRepository.PathHitTotal("/akordi/1/some-song", 1));
    }

    @Test
    void excludesBotTraffic() {
        aggregator.aggregate(List.of(
                requestLine("2026-07-20T10:00:00.000Z", "/", "-", "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T10:01:00.000Z", "/", "-", "Googlebot/2.1"),
                // Missing User-Agent entirely (RequestLoggingFilter's own
                // "-" placeholder for an absent header) counts as a bot too.
                requestLine("2026-07-20T10:02:00.000Z", "/", "-", "-")));

        assertThat(repository.totalHitsSince(DAY)).isEqualTo(1);
    }

    @Test
    void ignoresLinesFromOtherLoggersAndMalformedLines() {
        aggregator.aggregate(List.of(
                "2026-07-20T10:00:00.000Z  INFO 123 --- [vinoigitare-boot] [main] c.v.storage.SongImporter : "
                        + "Reconciled .tab files with the database: 0 added, 0 updated, 0 removed.",
                "not a log line at all"));

        assertThat(repository.totalHitsSince(DAY)).isZero();
    }

    @Test
    void groupsReferrersByHostAndFoldsMissingOrSameSiteReferrersIntoDirect() {
        aggregator.aggregate(List.of(
                requestLine("2026-07-20T10:00:00.000Z", "/", "https://www.google.com/search?q=akordi",
                        "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T10:01:00.000Z", "/", "https://www.google.com/search?q=other",
                        "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T10:02:00.000Z", "/", "-", "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T10:03:00.000Z", "/", "https://vinoigitare.com/some-other-page",
                        "Mozilla/5.0 Real Browser")));

        assertThat(repository.topReferrersSince(DAY, 10)).containsExactly(
                new LogAnalyticsRepository.ReferrerHitTotal(LogAnalyticsRepository.DIRECT_REFERRER, 2),
                new LogAnalyticsRepository.ReferrerHitTotal("www.google.com", 2));
    }

    @Test
    void secondAggregationRunOnTheSameLinesDoesNotDoubleCount() {
        List<String> lines = List.of(
                requestLine("2026-07-20T10:00:00.000Z", "/", "-", "Mozilla/5.0 Real Browser"));

        aggregator.aggregate(lines);
        aggregator.aggregate(lines);

        assertThat(repository.totalHitsSince(DAY)).isEqualTo(1);
    }

    @Test
    void onlyLinesNewerThanTheLastRunAreCounted() {
        aggregator.aggregate(List.of(requestLine("2026-07-20T10:00:00.000Z", "/", "-", "Mozilla/5.0 Real Browser")));

        aggregator.aggregate(List.of(
                requestLine("2026-07-20T10:00:00.000Z", "/", "-", "Mozilla/5.0 Real Browser"),
                requestLine("2026-07-20T11:00:00.000Z", "/second-page", "-", "Mozilla/5.0 Real Browser")));

        assertThat(repository.totalHitsSince(DAY)).isEqualTo(2);
        assertThat(repository.topPathsSince(DAY, 10)).containsExactlyInAnyOrder(
                new LogAnalyticsRepository.PathHitTotal("/", 1),
                new LogAnalyticsRepository.PathHitTotal("/second-page", 1));
    }

    private static String requestLine(String isoTimestamp, String path, String referer, String userAgent) {
        return isoTimestamp + "  INFO 123 --- [vinoigitare-boot] [           main] "
                + "c.v.logging.RequestLoggingFilter         : GET " + path + " -> 200 (5 ms) [203.0.113.5] "
                + "referer=\"" + referer + "\" ua=\"" + userAgent + "\"";
    }
}
