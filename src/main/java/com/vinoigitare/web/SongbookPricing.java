package com.vinoigitare.web;

/**
 * Tiered pricing for a generated songbook PDF, by song count -- Phase B
 * (~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md,
 * §1b, decided 2026-07-17, superseding the original flat $5 fee).
 *
 * <p>Boundaries are strict less-than: a 100-song selection lands in the
 * $10 tier, not the $5 one; a 300-song selection lands in the $15 tier,
 * not the $10 one (see the plan doc's note on this being an assumed, not
 * explicitly confirmed, reading of "under 100"/"under 300").
 */
public final class SongbookPricing {

    private SongbookPricing() {
    }

    public static int amountCentsFor(int songCount) {
        if (songCount < 100) {
            return 500;
        }
        if (songCount < 300) {
            return 1000;
        }
        return 1500;
    }
}
