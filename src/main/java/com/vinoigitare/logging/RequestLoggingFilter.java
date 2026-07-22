package com.vinoigitare.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every request this app actually cares about -- method, path, status,
 * duration, remote IP, referer, user-agent -- at INFO. See
 * {@code ~/knowledge/projects/vinoigitare/production-logging-plan.md} and,
 * for the referer/user-agent fields specifically (analytics Part 3, 2026-07-22),
 * {@code ~/knowledge/projects/vinoigitare/analytics-plan.md}.
 *
 * <p>Static assets are excluded to keep the log's signal-to-noise ratio
 * high: a single page view pulls in a dozen CSS/JS/icon requests that add
 * nothing worth searching for later. {@link #isStaticAsset(String)} reuses
 * the same route categories {@code static/sw.js}'s own {@code
 * isStaticAsset} already excludes from its caching logic, rather than
 * redefining an equivalent list in a second place.
 *
 * <p>{@code request.getRemoteAddr()} relies on the same {@code
 * server.forward-headers-strategy: framework} config that already fixes
 * scheme detection and the feedback form's rate limiter -- see either of
 * those for why this resolves the real client IP behind Caddy, not
 * Caddy's own address.
 *
 * <p><b>Log line format is a contract with {@code
 * com.vinoigitare.analytics.LogAnalyticsAggregator}</b>, which parses this
 * exact message shape back out of the rotated log file -- same
 * can't-literally-share-code-across-a-boundary situation as {@code
 * isStaticAsset} above, just a text format instead of a JS/Java split. If
 * this format ever changes, that parser's regex needs a matching update.
 * Referer/user-agent default to {@code "-"} (missing header) rather than
 * an empty string, so a missing value is never confused with an actually-
 * empty one when read back.
 *
 * <p><b>Real bug found 2026-07-17, caught by actually running the app and
 * checking the log file, not just reading the code:</b> a plain
 * {@code @Component}-annotated filter gets auto-registered by Spring Boot
 * with default (lowest) precedence, which runs it <i>after</i> Spring
 * Security's own filter chain. Any request Security redirects itself --
 * every unauthenticated hit on {@code /admin/**}, or any URL not on
 * {@code SecurityConfig}'s allowlist -- never reached this filter at all,
 * since {@code ExceptionTranslationFilter} sends the redirect directly
 * without calling {@code chain.doFilter()} any further. That's exactly
 * the kind of event worth logging (failed/unauthorized access attempts),
 * so it's registered explicitly with {@code Ordered.HIGHEST_PRECEDENCE}
 * in {@link LoggingConfig} instead of relying on {@code @Component}
 * auto-registration, ensuring it wraps the entire chain including
 * Security's own filters.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isStaticAsset(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String referer = headerOrDash(request, "Referer");
            String userAgent = headerOrDash(request, "User-Agent");
            LOG.info("{} {} -> {} ({} ms) [{}] referer=\"{}\" ua=\"{}\"", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), durationMs, request.getRemoteAddr(), referer,
                    userAgent);
        }
    }

    private static String headerOrDash(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        // Newlines stripped defensively: this value now gets re-parsed by
        // LogAnalyticsAggregator, so an attacker-controlled header is a
        // (low-stakes, analytics-only) log-injection vector it wasn't
        // before -- a crafted Referer/User-Agent with an embedded newline
        // could otherwise forge a second, fake log line.
        return value != null ? value.replace('\n', ' ').replace('\r', ' ') : "-";
    }

    private static boolean isStaticAsset(String path) {
        return path.startsWith("/webjars/") || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/icons/") || path.startsWith("/images/");
    }
}
