package com.vinoigitare.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Logs every login attempt against the single admin credential -- success
 * or failure -- at INFO. See
 * {@code ~/knowledge/projects/vinoigitare/production-logging-plan.md}.
 *
 * <p>Listens for the events Spring Security's {@code
 * DefaultAuthenticationEventPublisher} already publishes automatically
 * (auto-configured, nothing to wire in {@code SecurityConfig} itself) --
 * no filter-chain changes needed, this is purely observational.
 */
@Component
public class SecurityAuditListener {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityAuditListener.class);

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        LOG.info("Login succeeded for user '{}'", event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        LOG.warn("Login failed for user '{}': {}", event.getAuthentication().getName(),
                event.getException().getMessage());
    }
}
