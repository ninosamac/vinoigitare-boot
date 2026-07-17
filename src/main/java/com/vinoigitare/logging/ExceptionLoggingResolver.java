package com.vinoigitare.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Logs every exception that reaches Spring MVC's exception-handling
 * machinery, then returns {@code null} so the next resolver in the chain
 * (Spring Boot's own default handling, ultimately the Whitelabel error
 * page) still runs exactly as before -- purely observational, doesn't
 * change any response a visitor sees. See
 * {@code ~/knowledge/projects/vinoigitare/production-logging-plan.md}.
 *
 * <p>Before this, an unhandled exception was only ever visible as
 * whatever the error page showed the visitor -- nothing durable
 * server-side. {@link Ordered#HIGHEST_PRECEDENCE} ensures this runs
 * before any other resolver gets a chance to handle (and stop
 * propagation of) the exception.
 *
 * <p>{@link ResponseStatusException}s used deliberately for expected
 * client errors (e.g. {@code AdminController}'s 404 for an unknown song
 * id) are logged at WARN, not ERROR -- they're not application bugs.
 * Everything else is a real, unexpected failure.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExceptionLoggingResolver implements HandlerExceptionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionLoggingResolver.class);

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        String handlerDescription = handler instanceof HandlerMethod handlerMethod ? handlerMethod.toString()
                : String.valueOf(handler);
        if (ex instanceof ResponseStatusException responseStatusException) {
            LOG.warn("{} {} -> {} handled by {}: {}", request.getMethod(), request.getRequestURI(),
                    responseStatusException.getStatusCode(), handlerDescription, ex.getMessage());
        } else {
            LOG.error("{} {} -> unhandled exception in {}", request.getMethod(), request.getRequestURI(),
                    handlerDescription, ex);
        }
        return null;
    }
}
