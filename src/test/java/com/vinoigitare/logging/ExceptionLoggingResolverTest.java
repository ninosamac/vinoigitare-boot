package com.vinoigitare.logging;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * See ~/knowledge/projects/vinoigitare/production-logging-plan.md. The one
 * property that actually matters here: this resolver must never take over
 * exception handling itself (always returns null), or every existing
 * error response in this app -- 404s, the Whitelabel error page, etc. --
 * would silently break. Doesn't assert log content/level, matching this
 * codebase's existing convention of not testing log output directly.
 */
@Tag("fast")
class ExceptionLoggingResolverTest {

    private final ExceptionLoggingResolver resolver = new ExceptionLoggingResolver();

    @Test
    void alwaysReturnsNullForAResponseStatusException() {
        ModelAndView result = resolver.resolveException(new MockHttpServletRequest("GET", "/admin/edit/999"),
                new MockHttpServletResponse(), null,
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: 999"));

        assertThat(result).isNull();
    }

    @Test
    void alwaysReturnsNullForAnUnexpectedException() {
        ModelAndView result = resolver.resolveException(new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(), null, new IllegalStateException("boom"));

        assertThat(result).isNull();
    }

    @Test
    void handlesANullHandlerWithoutThrowing() {
        ModelAndView result = resolver.resolveException(new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(), null, new RuntimeException("no handler known"));

        assertThat(result).isNull();
    }
}
