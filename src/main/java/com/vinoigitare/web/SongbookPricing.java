package com.vinoigitare.web;

/**
 * Tiered pricing for a generated songbook PDF, by <b>page count</b> --
 * Phase B (~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md,
 * §1c, decided 2026-07-18, superseding §1b's song-count tiers; prices
 * raised 2026-07-19, same tier boundaries). Page count is only known
 * once the PDF is actually rendered (song length, chord diagrams,
 * transposition, and the include-diagrams toggle all affect how many
 * pages it comes out to, not just how many songs are in it) -- see
 * {@code SongbookCheckoutController#checkout}, which renders before
 * creating the Stripe Checkout Session specifically so the price
 * charged reflects the real page count, not an estimate.
 *
 * <p>Boundaries are strict less-than, same convention §1b used: a
 * 20-page book lands in the $5 tier, not the $3 one; a 50-page book
 * lands in the $7 tier, not the $5 one. {@link #exceedsMaxPages(int)}
 * resolves the one real ambiguity in how this was worded ("$5 for less
 * than 100" alongside "can't have more than 100" leaves page count 100
 * itself undefined by either statement) as: 50-99 pages is the top paid
 * tier, 100+ is rejected outright -- an assumption, not explicitly
 * confirmed, same as §1b's own boundary assumption before it.
 *
 * <p>Currency switched from USD to EUR (2026-07-22, Nino's own call --
 * the site serves an EU/Croatian-Serbian audience) -- see {@code
 * SongbookCheckoutController#checkout}'s {@code setCurrency("eur")}.
 * The tier amounts below were kept as-is rather than converted (USD and
 * EUR trade near parity), so every {@code $} in this Javadoc is now a
 * historical artifact of the amounts being decided in USD terms, not a
 * literal current price.
 */
public final class SongbookPricing {

    /** 100 or more pages is rejected outright -- no tier covers it, see the class Javadoc. */
    public static final int MAX_PAGES = 100;

    private SongbookPricing() {
    }

    public static boolean exceedsMaxPages(int pageCount) {
        return pageCount >= MAX_PAGES;
    }

    public static int amountCentsFor(int pageCount) {
        if (pageCount < 20) {
            return 300;
        }
        if (pageCount < 50) {
            return 500;
        }
        return 700;
    }
}
