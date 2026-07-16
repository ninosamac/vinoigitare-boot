package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class FeedbackRateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRejects() {
        FeedbackRateLimiter limiter = new FeedbackRateLimiter();

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void tracksEachIpIndependently() {
        FeedbackRateLimiter limiter = new FeedbackRateLimiter();

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();

        // A different IP has its own, still-fresh budget.
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }
}
