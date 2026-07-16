package com.vinoigitare.web;

/**
 * Thrown when {@link ResendEmailClient} fails to send -- the Resend API
 * returned an error status, or the HTTP call itself failed. Callers treat
 * this the same way {@code MailException} used to before the SMTP-to-Resend
 * switch (~/knowledge/projects/vinoigitare/visitor-feedback-form-plan.md, §8).
 */
public class ResendEmailException extends RuntimeException {

    public ResendEmailException(String message) {
        super(message);
    }

    public ResendEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
