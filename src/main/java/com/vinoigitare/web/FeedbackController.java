package com.vinoigitare.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Visitor feedback form, Tier 1 -- comment + optional name/email, emailed
 * to a single address, never stored or displayed. See
 * {@code ~/knowledge/projects/vinoigitare/visitor-feedback-form-plan.md}.
 *
 * <p>Public and unauthenticated (added to {@code SecurityConfig}'s
 * allowlist) -- this app's first genuinely public POST endpoint; CSRF
 * protection still applies regardless (that's a separate concern from
 * authorization), and {@code about.html}'s {@code th:action} form gets the
 * token auto-injected the same way every other form in this app already
 * does.
 */
@Controller
public class FeedbackController {

    private static final Logger LOG = LoggerFactory.getLogger(FeedbackController.class);

    private final ResendEmailClient emailClient;
    private final FeedbackRateLimiter rateLimiter;
    private final String toEmail;

    public FeedbackController(ResendEmailClient emailClient, FeedbackRateLimiter rateLimiter,
            @Value("${vinoigitare.feedback-to-email}") String toEmail) {
        this.emailClient = emailClient;
        this.rateLimiter = rateLimiter;
        this.toEmail = toEmail;
    }

    /**
     * {@code website} is the honeypot -- a real visitor never sees or
     * fills this field (see {@code about.html}'s comment on how it's
     * hidden). A non-blank value here always redirects as if the
     * submission succeeded, without sending anything and without even
     * looking at validation/rate-limit results -- the visitor is never
     * tipped off that anything was caught, which would just teach a bot
     * to leave the field blank next time.
     *
     * <p>Validation failures and rate-limit rejections both redirect to
     * the same generic {@code ?feedbackError} -- no per-field error
     * redisplay for a first version of this form, consistent with the
     * plan's "keep it simple" framing throughout.
     */
    @PostMapping("/feedback")
    public String submit(@Valid @ModelAttribute FeedbackForm form, BindingResult bindingResult,
            HttpServletRequest request) {
        if (!form.website().isBlank()) {
            return "redirect:/about?feedbackSent";
        }
        if (bindingResult.hasErrors()) {
            return "redirect:/about?feedbackError";
        }
        // Spring's forward-headers-strategy: framework (application.yml)
        // already resolves this to the real client IP behind Caddy's
        // reverse proxy, the same forwarded-header handling that fixes
        // scheme detection for SitemapController -- no separate logic
        // needed here.
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            return "redirect:/about?feedbackError";
        }
        try {
            sendEmail(form);
        } catch (ResendEmailException e) {
            LOG.warn("Could not send visitor feedback email", e);
            return "redirect:/about?feedbackError";
        }
        return "redirect:/about?feedbackSent";
    }

    private void sendEmail(FeedbackForm form) {
        String from = form.name().isBlank() ? "Anonymous" : form.name();
        String subject = "Vino i gitare feedback from " + from;
        emailClient.send(toEmail, subject, form.comment(), form.email().isBlank() ? null : form.email());
    }

    /** Matches the visible form fields in about.html exactly, plus the website honeypot. */
    public record FeedbackForm(
            @NotBlank @Size(max = 2000) String comment,
            @Size(max = 100) String name,
            @Email @Size(max = 200) String email,
            String website) {
    }
}
