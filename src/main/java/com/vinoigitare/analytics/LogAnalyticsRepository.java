package com.vinoigitare.analytics;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * H2-backed persistence for the day-granularity analytics
 * {@link LogAnalyticsAggregator} builds -- plain {@link JdbcTemplate},
 * matching {@code DatabaseSongRepository}'s existing convention. See
 * {@code schema.sql}'s {@code daily_page_hit}/{@code daily_referrer_hit}/
 * {@code log_analytics_state} tables.
 *
 * <p>Increment methods use an update-then-insert-if-zero-rows upsert
 * rather than a single {@code MERGE} statement, since H2's {@code MERGE}
 * replaces a row's values rather than incrementing them -- this only ever
 * has one writer ({@link LogAnalyticsAggregator}'s single {@code
 * @Scheduled} method), so there's no concurrent-write race to guard
 * against.
 */
@Repository
public class LogAnalyticsRepository {

    /** Referrer host stored for a request with no Referer header at all. */
    static final String DIRECT_REFERRER = "direct";

    private final JdbcTemplate jdbcTemplate;

    public LogAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Instant lastProcessedAt() {
        List<Timestamp> rows = jdbcTemplate.query(
                "SELECT last_processed_at FROM log_analytics_state WHERE id = 1",
                (rs, rowNum) -> rs.getTimestamp("last_processed_at"));
        return rows.isEmpty() ? Instant.EPOCH : rows.get(0).toInstant();
    }

    void saveLastProcessedAt(Instant instant) {
        int updated = jdbcTemplate.update("UPDATE log_analytics_state SET last_processed_at = ? WHERE id = 1",
                Timestamp.from(instant));
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO log_analytics_state (id, last_processed_at) VALUES (1, ?)",
                    Timestamp.from(instant));
        }
    }

    void incrementPageHits(LocalDate date, String path, long increment) {
        int updated = jdbcTemplate.update("UPDATE daily_page_hit SET hits = hits + ? WHERE hit_date = ? AND path = ?",
                increment, Date.valueOf(date), path);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO daily_page_hit (hit_date, path, hits) VALUES (?, ?, ?)",
                    Date.valueOf(date), path, increment);
        }
    }

    void incrementReferrerHits(LocalDate date, String referrerHost, long increment) {
        int updated = jdbcTemplate.update(
                "UPDATE daily_referrer_hit SET hits = hits + ? WHERE hit_date = ? AND referrer_host = ?", increment,
                Date.valueOf(date), referrerHost);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO daily_referrer_hit (hit_date, referrer_host, hits) VALUES (?, ?, ?)",
                    Date.valueOf(date), referrerHost, increment);
        }
    }

    public long totalHitsSince(LocalDate since) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(hits), 0) FROM daily_page_hit WHERE hit_date >= ?", Long.class,
                Date.valueOf(since));
        return total != null ? total : 0L;
    }

    public List<PathHitTotal> topPathsSince(LocalDate since, int limit) {
        return jdbcTemplate.query(
                "SELECT path, SUM(hits) AS hits FROM daily_page_hit WHERE hit_date >= ? "
                        + "GROUP BY path ORDER BY hits DESC LIMIT ?",
                PATH_HIT_TOTAL_MAPPER, Date.valueOf(since), limit);
    }

    public List<ReferrerHitTotal> topReferrersSince(LocalDate since, int limit) {
        return jdbcTemplate.query(
                "SELECT referrer_host, SUM(hits) AS hits FROM daily_referrer_hit WHERE hit_date >= ? "
                        + "GROUP BY referrer_host ORDER BY hits DESC LIMIT ?",
                REFERRER_HIT_TOTAL_MAPPER, Date.valueOf(since), limit);
    }

    private static final RowMapper<PathHitTotal> PATH_HIT_TOTAL_MAPPER =
            (ResultSet rs, int rowNum) -> new PathHitTotal(rs.getString("path"), rs.getLong("hits"));

    private static final RowMapper<ReferrerHitTotal> REFERRER_HIT_TOTAL_MAPPER =
            (ResultSet rs, int rowNum) -> new ReferrerHitTotal(rs.getString("referrer_host"), rs.getLong("hits"));

    /** @param path the request path (e.g. {@code /akordi/1/some-song}) */
    public record PathHitTotal(String path, long hits) {
    }

    /**
     * @param referrerHost the referring host (e.g. {@code google.com}), or
     *                      {@value LogAnalyticsRepository#DIRECT_REFERRER}
     *                      for requests with no Referer header
     */
    public record ReferrerHitTotal(String referrerHost, long hits) {
    }
}
