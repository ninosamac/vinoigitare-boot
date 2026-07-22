package com.vinoigitare.analytics;

import java.util.List;
import java.util.Locale;

/**
 * Best-effort crawler/bot detection for {@link LogAnalyticsAggregator}, by a
 * short, hand-maintained substring blocklist against the request's
 * User-Agent -- see analytics-plan.md's Part 3, "not aiming for a complete
 * bot-detection system", just enough that real access logs (dominated by
 * search-engine crawlers and scrapers) don't wildly overstate real visitor
 * traffic in the daily hit counts.
 */
final class BotFilter {

    private static final List<String> BOT_MARKERS = List.of(
            "bot", "crawl", "spider", "slurp", "mediapartners", "facebookexternalhit", "yandex", "petalbot",
            "semrush", "ahrefs", "mj12bot", "dotbot", "applebot", "duckduckbot", "bingpreview", "archive.org_bot");

    private BotFilter() {
    }

    static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || userAgent.equals("-")) {
            // Real browsers always send a User-Agent; a request with none
            // is more likely a script/bot than a browser, so it's treated
            // the same as a recognized bot rather than counted as a visit.
            return true;
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        for (String marker : BOT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
