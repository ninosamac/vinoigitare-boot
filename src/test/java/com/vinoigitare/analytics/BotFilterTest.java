package com.vinoigitare.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class BotFilterTest {

    @Test
    void realBrowserUserAgentsAreNotBots() {
        assertThat(BotFilter.isBot(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 "
                        + "Safari/537.36")).isFalse();
        assertThat(BotFilter.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"))
                .isFalse();
    }

    @Test
    void knownCrawlerUserAgentsAreBotsRegardlessOfCase() {
        assertThat(BotFilter.isBot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"))
                .isTrue();
        assertThat(BotFilter.isBot("Mozilla/5.0 (compatible; BINGBOT/2.0; +http://www.bing.com/bingbot.htm)"))
                .isTrue();
        assertThat(BotFilter.isBot("AhrefsBot/7.0 (+http://ahrefs.com/robot/)")).isTrue();
        assertThat(BotFilter.isBot("Mozilla/5.0 (compatible; SemrushBot/7~bl; +http://www.semrush.com/bot.html)"))
                .isTrue();
    }

    @Test
    void missingOrBlankUserAgentIsTreatedAsABot() {
        assertThat(BotFilter.isBot(null)).isTrue();
        assertThat(BotFilter.isBot("")).isTrue();
        assertThat(BotFilter.isBot("   ")).isTrue();
        // RequestLoggingFilter's own placeholder for an absent header.
        assertThat(BotFilter.isBot("-")).isTrue();
    }
}
