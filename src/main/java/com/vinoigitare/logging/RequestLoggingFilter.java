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
 * duration, remote IP -- at INFO. See
 * {@code ~/knowledge/projects/vinoigitare/production-logging-plan.md}.
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
            LOG.info("{} {} -> {} ({} ms) [{}]", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    durationMs, request.getRemoteAddr());
        }
    }

    private static boolean isStaticAsset(String path) {
        return path.startsWith("/webjars/") || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/icons/") || path.startsWith("/images/");
    }
}
