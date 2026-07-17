package com.vinoigitare.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RequestLoggingFilter} explicitly, with the highest
 * possible precedence, rather than letting Spring Boot auto-register it
 * as a plain {@code @Component} -- see that class's Javadoc for the real
 * bug this fixes (auto-registration runs after Spring Security's filter
 * chain, silently missing every request Security redirects itself).
 */
@Configuration
public class LoggingConfig {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(
                new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
