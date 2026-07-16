package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.security.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Visitor feedback form, Tier 1. See {@code ~/knowledge/projects/vinoigitare/visitor-feedback-form-plan.md}.
 *
 * <p>Unlike {@code AdminControllerTest}/{@code SongbookControllerTest},
 * {@code /feedback} is genuinely public (on {@link SecurityConfig}'s
 * allowlist) -- these tests confirm CSRF is still enforced regardless,
 * and that the honeypot/validation/rate-limit paths all behave correctly
 * without ever needing real SMTP credentials (JavaMailSender is mocked).
 */
@Tag("fast")
@WebMvcTest(FeedbackController.class)
@Import({ SecurityConfig.class, FeedbackRateLimiter.class })
class FeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private FeedbackRateLimiter rateLimiter;

    @Test
    void submitWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/feedback").param("comment", "Hello").param("name", "").param("email", "")
                        .param("website", ""))
                .andExpect(status().isForbidden());

        then(mailSender).should(never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void validSubmissionSendsEmailAndRedirectsToFeedbackSent() throws Exception {
        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "Love the site!")
                        .param("name", "Marko")
                        .param("email", "marko@example.com")
                        .param("website", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about?feedbackSent"));

        var captor = forClass(SimpleMailMessage.class);
        then(mailSender).should().send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getText()).isEqualTo("Love the site!");
        assertThat(sent.getSubject()).contains("Marko");
        assertThat(sent.getReplyTo()).isEqualTo("marko@example.com");
    }

    @Test
    void submissionWithoutNameUsesAnonymousInSubject() throws Exception {
        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "Anonymous thought.")
                        .param("name", "")
                        .param("email", "")
                        .param("website", ""))
                .andExpect(status().is3xxRedirection());

        var captor = forClass(SimpleMailMessage.class);
        then(mailSender).should().send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("Anonymous");
        assertThat(captor.getValue().getReplyTo()).isNull();
    }

    @Test
    void honeypotFilledSilentlyDropsSubmissionButStillRedirectsToFeedbackSent() throws Exception {
        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "I am a bot")
                        .param("name", "")
                        .param("email", "")
                        .param("website", "http://spam.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about?feedbackSent"));

        then(mailSender).should(never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void blankCommentFailsValidationAndRedirectsToFeedbackErrorWithoutSending() throws Exception {
        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "")
                        .param("name", "")
                        .param("email", "")
                        .param("website", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about?feedbackError"));

        then(mailSender).should(never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void malformedEmailFailsValidationAndRedirectsToFeedbackErrorWithoutSending() throws Exception {
        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "Hi")
                        .param("name", "")
                        .param("email", "not-an-email")
                        .param("website", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about?feedbackError"));

        then(mailSender).should(never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void rateLimitExceededRedirectsToFeedbackErrorWithoutSending() throws Exception {
        given(rateLimiter.tryAcquire(anyString())).willReturn(false);

        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "One more please")
                        .param("name", "")
                        .param("email", "")
                        .param("website", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about?feedbackError"));

        then(mailSender).should(never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailSenderFailureRedirectsToFeedbackErrorGracefully() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/feedback").with(csrf())
                        .param("comment", "Testing failure")
                        .param("name", "")
                        .param("email", "")
                        .param("website", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about?feedbackError"));
    }
}
