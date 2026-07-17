package com.vinoigitare.web;

import org.springframework.stereotype.Component;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * Thin wrapper around {@link Session#create(SessionCreateParams)} -- a
 * static SDK call can't be mocked via ordinary dependency injection, so
 * this exists purely as a testable seam for {@link
 * SongbookCheckoutController}, the same reasoning {@code
 * ResendEmailClient} wraps an HTTP call rather than a controller invoking
 * it directly.
 */
@Component
public class StripeCheckoutSessionCreator {

    public Session create(SessionCreateParams params) throws StripeException {
        return Session.create(params);
    }
}
