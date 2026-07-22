package com.vinoigitare.analytics;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Analytics, Part 3 (analytics-plan.md) -- builds day-granularity hit/
 * referrer aggregates out of the request log {@code RequestLoggingFilter}
 * already writes, rather than a raw-log-parsing read path: journald's
 * retention and the rotated log file itself aren't permanent, so this
 * writes durable counts into {@code daily_page_hit}/{@code
 * daily_referrer_hit} ({@link LogAnalyticsRepository}) instead.
 *
 * <p><b>In-app {@code @Scheduled} job, not an external cron script</b> --
 * a deliberate deviation from analytics-plan.md's original sketch (which
 * assumed a shell script on the songs-sync script's own cron cadence).
 * {@code com.vinoigitare.storage.SongImporter} already established this
 * exact pattern (periodic reconciliation via {@code @Scheduled}, enabled
 * once via {@code @EnableScheduling}) for a similar "keep the database in
 * sync with something on disk" job -- reusing it here means no new
 * crontab entry or script to deploy/maintain on the VPS, at the cost of
 * only running while the app itself is up (acceptable: the app being down
 * means nothing is being served to aggregate anyway).
 *
 * <p>Resumes from a persisted cursor ({@link LogAnalyticsRepository#lastProcessedAt()})
 * rather than tracking a byte offset into the log file, so log rotation
 * (see production-logging-plan.md) never needs special-casing -- every
 * line still carries its own timestamp regardless of which physical file
 * it ends up in.
 */
@Component
public class LogAnalyticsAggregator {

    private static final Log log = LogFactory.getLog(LogAnalyticsAggregator.class.getName());

    /**
     * Matches {@code RequestLoggingFilter}'s message exactly -- see that
     * class's Javadoc; this is the other half of that documented contract.
     * Deliberately unanchored (searched via {@link Matcher#find()}, not
     * matched against the whole line) so it doesn't need to also account
     * for Logback's own timestamp/level/logger prefix formatting.
     */
    private static final Pattern REQUEST_LINE = Pattern.compile(
            "(?:GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) (\\S+) -> \\d{3} \\(\\d+ ms\\) \\[[^\\]]*\\] "
                    + "referer=\"([^\"]*)\" ua=\"([^\"]*)\"");

    private static final int MAX_PATH_LENGTH = 500;
    private static final int MAX_REFERRER_HOST_LENGTH = 255;

    private final LogAnalyticsRepository repository;
    private final Path logFile;

    public LogAnalyticsAggregator(LogAnalyticsRepository repository,
            @Value("${logging.file.name}") String logFileName) {
        this.repository = repository;
        this.logFile = Path.of(logFileName);
    }

    /**
     * Hourly is plenty for a low-traffic solo hobby site (analytics-plan.md)
     * -- {@code @Scheduled(fixedRate=...)} fires its first tick immediately
     * at startup and then hourly for the rest of this instance's uptime,
     * same as {@code SongImporter}'s own scheduled reconciliation.
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    void aggregateOnSchedule() {
        if (!Files.exists(logFile)) {
            // Fresh checkout that's never actually run yet -- nothing to
            // aggregate, and nothing wrong either.
            return;
        }
        try {
            aggregate(Files.readAllLines(logFile));
        } catch (IOException e) {
            log.warn("Could not read " + logFile + " for log-based analytics aggregation", e);
        }
    }

    /**
     * Package-private so tests can feed fixture lines directly rather than
     * needing a real file on disk at the configured path.
     */
    void aggregate(List<String> lines) {
        Instant since = repository.lastProcessedAt();
        Instant newestSeen = since;

        Map<DateAndKey, Long> pageHits = new HashMap<>();
        Map<DateAndKey, Long> referrerHits = new HashMap<>();

        for (String line : lines) {
            Instant timestamp = extractTimestamp(line);
            if (timestamp == null || !timestamp.isAfter(since)) {
                continue;
            }
            if (timestamp.isAfter(newestSeen)) {
                newestSeen = timestamp;
            }

            Matcher matcher = REQUEST_LINE.matcher(line);
            if (!matcher.find()) {
                // Some other logger's line (or a stack-trace continuation)
                // -- not one of ours, nothing to count, but its timestamp
                // still advanced the cursor above.
                continue;
            }
            String userAgent = matcher.group(3);
            if (BotFilter.isBot(userAgent)) {
                continue;
            }

            String path = truncate(matcher.group(1), MAX_PATH_LENGTH);
            String referrerHost = truncate(referrerHost(matcher.group(2)), MAX_REFERRER_HOST_LENGTH);
            LocalDate date = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
            pageHits.merge(new DateAndKey(date, path), 1L, Long::sum);
            referrerHits.merge(new DateAndKey(date, referrerHost), 1L, Long::sum);
        }

        pageHits.forEach((key, count) -> repository.incrementPageHits(key.date(), key.value(), count));
        referrerHits.forEach((key, count) -> repository.incrementReferrerHits(key.date(), key.value(), count));

        if (newestSeen.isAfter(since)) {
            repository.saveLastProcessedAt(newestSeen);
        }
    }

    /**
     * The line's leading token, up to the first space -- Logback's default
     * pattern always starts a line with its ISO-8601 timestamp. Parsed as
     * an offset date-time (not {@code Instant.parse}, which only accepts a
     * literal {@code Z} suffix) since local dev runs in the system's own
     * zone offset (e.g. {@code +02:00}) while production runs in UTC
     * ({@code Z}) -- both are valid ISO-8601 offsets.
     */
    private static Instant extractTimestamp(String line) {
        int firstSpace = line.indexOf(' ');
        if (firstSpace < 0) {
            return null;
        }
        try {
            return OffsetDateTime.parse(line.substring(0, firstSpace), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Same-site navigation isn't a real inbound referrer -- folded into
     * {@link LogAnalyticsRepository#DIRECT_REFERRER} rather than its own
     * category, so "top referrers" reflects actual external traffic
     * sources instead of being dominated by internal link clicks.
     */
    private static String referrerHost(String referer) {
        if (referer == null || referer.equals("-") || referer.isBlank()) {
            return LogAnalyticsRepository.DIRECT_REFERRER;
        }
        try {
            String host = new URI(referer).getHost();
            if (host == null || host.isBlank()) {
                return LogAnalyticsRepository.DIRECT_REFERRER;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("vinoigitare.com") || host.equals("www.vinoigitare.com")) {
                return LogAnalyticsRepository.DIRECT_REFERRER;
            }
            return host;
        } catch (URISyntaxException e) {
            return LogAnalyticsRepository.DIRECT_REFERRER;
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record DateAndKey(LocalDate date, String value) {
    }
}
