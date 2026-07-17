package com.vinoigitare.web;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * A pending or paid songbook-PDF purchase -- Phase B
 * (~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md,
 * §2). Persisted via {@link SongbookRequestRepository}.
 *
 * @param id opaque, high-entropy random id -- this <b>is</b> the entire
 *           access control for a paid download (see {@link #newId()}),
 *           never a sequential/guessable value.
 * @param selection the visitor's selection, as the exact JSON shape
 *                   {@code static/js/songbook.js} already produces
 *                   ({@code [{id, transpose}, ...]}) -- stored verbatim,
 *                   parsed again only when actually generating the PDF.
 * @param bookTitle custom cover title, nullable -- same meaning as
 *                   {@link com.vinoigitare.pdf.SongbookPdfRenderer}'s own
 *                   {@code bookTitle} parameter.
 * @param includeChordDiagrams whether to include the chord-reference section.
 * @param songCount number of songs in the selection at request-creation
 *                  time -- persisted rather than re-derived from {@link
 *                  #selection()} each time, since it's also what {@link
 *                  #amountCents()} was computed from and both should stay
 *                  consistent with whatever was actually charged.
 * @param amountCents the price actually charged, in cents (see {@link
 *                     SongbookPricing}) -- fixed at request-creation time,
 *                     so a later change to the pricing tiers can never
 *                     retroactively change what a pending/completed
 *                     purchase is considered to have cost.
 * @param paid whether Stripe's webhook has confirmed payment.
 * @param createdAt when this request was created.
 * @param paidAt when the webhook confirmed payment, nullable until then.
 */
public record SongbookRequest(
        String id,
        String selection,
        String bookTitle,
        boolean includeChordDiagrams,
        int songCount,
        int amountCents,
        boolean paid,
        Instant createdAt,
        Instant paidAt) {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 256 bits of randomness, URL-safe base64 without padding -- used directly as a /songbook/view/{id} path segment. */
    public static String newId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static SongbookRequest createNew(String selection, String bookTitle, boolean includeChordDiagrams,
            int songCount) {
        return new SongbookRequest(newId(), selection, bookTitle, includeChordDiagrams, songCount,
                SongbookPricing.amountCentsFor(songCount), false, Instant.now(), null);
    }
}
