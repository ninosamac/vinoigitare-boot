package com.vinoigitare.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Per-IP rate limiting for {@code POST /feedback} -- no accounts exist in
 * this app, so IP is the only practical axis (see
 * ~/knowledge/projects/vinoigitare/visitor-feedback-form-plan.md §3).
 *
 * <p>In-memory, not a DB table: a deliberate simplification -- losing this
 * state on a redeploy is an acceptable, minor gap for spam mitigation
 * specifically, not a real security control that needs to survive
 * restarts. A stale IP's deque is only pruned lazily, on its own next
 * check, rather than via a background sweep -- fine at this app's scale,
 * and avoids adding a scheduled task for a handful of entries.
 */
@Component
public class FeedbackRateLimiter {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();

    /**
     * @return true if this IP is still under the limit (and the attempt is
     *         now recorded), false if it should be rejected
     */
    public boolean tryAcquire(String clientIp) {
        Deque<Instant> attempts = attemptsByIp.computeIfAbsent(clientIp, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_ATTEMPTS) {
                return false;
            }
            attempts.addLast(Instant.now());
            return true;
        }
    }
}
