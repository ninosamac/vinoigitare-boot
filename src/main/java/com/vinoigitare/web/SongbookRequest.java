package com.vinoigitare.web;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * A pending or paid songbook-PDF purchase -- Phase B
 * (~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md,
 * §2). Persisted via {@link SongbookRequestRepository}.
 *
 * <p><b>{@code pdfBytes} is a record component of type {@code byte[]},
 * so the auto-generated {@code equals()}/{@code hashCode()} compare it
 * by reference, not content</b> (arrays don't override {@code Object}'s
 * default {@code equals()}) -- {@code SongbookRequestRepositoryTest}
 * deliberately compares fields individually rather than whole-record
 * equality for exactly this reason, not just the separate
 * timestamp-precision issue that already required that approach.
 *
 * @param id opaque, high-entropy random id -- this <b>is</b> the entire
 *           access control for a paid download (see {@link #newId()}),
 *           never a sequential/guessable value.
 * @param selection the visitor's selection, as the exact JSON shape
 *                   {@code static/js/songbook.js} already produces
 *                   ({@code [{id, transpose}, ...]}) -- stored verbatim.
 * @param bookTitle custom cover title, nullable -- same meaning as
 *                   {@link com.vinoigitare.pdf.SongbookPdfRenderer}'s own
 *                   {@code bookTitle} parameter.
 * @param includeChordDiagrams whether the chord-reference section was included.
 * @param songCount number of songs in the selection at request-creation
 *                  time -- informational only since pricing moved to
 *                  page count (§1c); no longer what {@link #amountCents()}
 *                  is derived from.
 * @param pageCount actual rendered page count of {@link #pdfBytes()} --
 *                  what {@link #amountCents()} is actually priced from
 *                  (see {@link SongbookPricing}), determined by rendering
 *                  the PDF at checkout time, before payment, since page
 *                  count depends on far more than just song count (song
 *                  length, transposition, whether chord diagrams were
 *                  included) and can't be estimated reliably otherwise.
 * @param amountCents the price actually charged, in cents (see {@link
 *                     SongbookPricing}) -- fixed at request-creation time,
 *                     so a later change to the pricing tiers can never
 *                     retroactively change what a pending/completed
 *                     purchase is considered to have cost.
 * @param pdfBytes the actual rendered PDF, generated once at checkout
 *                 time and served as-is at download time rather than
 *                 re-rendered -- guarantees the file a visitor downloads
 *                 is exactly the one that was priced and paid for, even
 *                 if the underlying song data changes in between, and
 *                 avoids rendering the same book twice.
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
        int pageCount,
        int amountCents,
        byte[] pdfBytes,
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
            int songCount, int pageCount, byte[] pdfBytes) {
        return new SongbookRequest(newId(), selection, bookTitle, includeChordDiagrams, songCount, pageCount,
                SongbookPricing.amountCentsFor(pageCount), pdfBytes, false, Instant.now(), null);
    }
}
