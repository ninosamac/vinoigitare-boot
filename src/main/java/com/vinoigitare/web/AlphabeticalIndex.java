package com.vinoigitare.web;

/**
 * First-letter bucketing for the homepage artist tree ({@link
 * SongBrowseController}). Originally shared with a genre A-Z index
 * (Phase 4c) that has since been removed -- kept as its own class rather
 * than folded back into the controller, in case a second caller shows up
 * again.
 *
 * <p>Simplified to "first Unicode code point, uppercased" -- this correctly
 * buckets diacritic letters (Đ, Š, Č, Ć, Ž) as their own letters (matching
 * pesmarica.rs's own alphabet, which does the same), but does NOT implement
 * the full Serbian/Croatian alphabet's two-character letters (Lj, Nj, Dž)
 * as single buckets -- e.g. "Ljubomir" groups under "L", not a separate
 * "Lj". Acceptable simplification carried over from Phase 4c; a known
 * limitation if it's ever worth fixing.
 */
final class AlphabeticalIndex {

    private AlphabeticalIndex() {
    }

    static char firstLetter(String text) {
        return text.isEmpty() ? '#' : Character.toUpperCase(text.charAt(0));
    }
}
