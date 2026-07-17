package com.vinoigitare.logging;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * See ~/knowledge/projects/vinoigitare/production-logging-plan.md. Doesn't
 * assert actual log output/content (no other test in this codebase does
 * either -- FeedbackControllerTest's mail-failure WARN isn't asserted) --
 * just the two behaviors that actually matter: static assets are excluded,
 * and the filter chain always still runs (this must never swallow or
 * short-circuit a real request).
 */
@Tag("fast")
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void staticAssetPathsAreExcluded() {
        assertThat(filter.shouldNotFilter(requestFor("/css/app.css"))).isTrue();
        assertThat(filter.shouldNotFilter(requestFor("/js/transpose.js"))).isTrue();
        assertThat(filter.shouldNotFilter(requestFor("/webjars/bootstrap/5.3.8/css/bootstrap.min.css"))).isTrue();
        assertThat(filter.shouldNotFilter(requestFor("/icons/favicon.svg"))).isTrue();
        assertThat(filter.shouldNotFilter(requestFor("/images/nav-brand.png"))).isTrue();
    }

    @Test
    void nonStaticPathsAreNotExcluded() {
        assertThat(filter.shouldNotFilter(requestFor("/"))).isFalse();
        assertThat(filter.shouldNotFilter(requestFor("/akordi/1/some-song"))).isFalse();
        assertThat(filter.shouldNotFilter(requestFor("/admin"))).isFalse();
        assertThat(filter.shouldNotFilter(requestFor("/feedback"))).isFalse();
    }

    @Test
    void alwaysCallsTheFilterChain() throws Exception {
        MockHttpServletRequest request = requestFor("/akordi/1/some-song");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        then(chain).should().doFilter(any(), any());
    }

    private static MockHttpServletRequest requestFor(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
