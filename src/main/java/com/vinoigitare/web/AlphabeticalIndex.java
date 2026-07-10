package com.vinoigitare.web;

/**
 * Shared first-letter bucketing for alphabetical browsing -- used by both
 * the genre A-Z index ({@link GenreBrowseController}, Phase 4c) and the
 * homepage artist tree ({@link SongBrowseController}), kept in exactly one
 * place so the two features can't quietly disagree on which letter a name
 * buckets under.
 *
 * <p>Simplified to "first Unicode code point, uppercased" -- this correctly
 * buckets diacritic letters (Đ, Š, Č, Ć, Ž) as their own letters (matching
 * pesmarica.rs's own alphabet, which does the same), but does NOT implement
 * the full Serbian/Croatian alphabet's two-character letters (Lj, Nj, Dž)
 * as single buckets -- e.g. "Ljubomir" groups under "L", not a separate
 * "Lj". Acceptable simplification carried over from Phase 4c; a known
 * limitation if it's ever worth fixing (it would need to change here once,
 * for both features at once).
 */
final class AlphabeticalIndex {

    private AlphabeticalIndex() {
    }

    static char firstLetter(String text) {
        return text.isEmpty() ? '#' : Character.toUpperCase(text.charAt(0));
    }
}
