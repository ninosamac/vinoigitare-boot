package com.vinoigitare.model;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Real bug found 2026-07-19 (Nino): every artist/song name sort in this app
 * used {@code String.CASE_INSENSITIVE_ORDER} (or plain {@code Character}
 * ordering for the homepage's per-letter grouping), which compares by raw
 * Unicode code point -- correct for the plain A-Z range, but wrong for
 * {@code č ć đ š ž}, whose code points all fall well after {@code z}
 * (Latin Extended-A, U+0100 range). Result: every artist/song starting with
 * one of those five letters sorted after every plain-Z one instead of
 * appearing where the real Croatian/Serbian (Latin-script) alphabet actually
 * places them -- {@code Č}/{@code Ć} right after {@code C}, {@code Đ} right
 * after {@code D}, {@code Š} right after {@code S}, and only {@code Ž}
 * genuinely belongs at the very end (it really is the last letter of the
 * alphabet).
 *
 * <p>Fix: a real, locale-aware {@link Collator} instead of code-point
 * comparison. {@code Locale.of("hr")} specifically (not {@code "sr"}) --
 * confirmed by direct comparison ({@code Collator.getInstance(Locale.of(
 * "hr")).compare(...)}) that the JDK's built-in Croatian collation rules
 * already place these letters exactly right: {@code C, Č, Ć, D, Đ, ..., S,
 * Š, ..., Z, Ž}. Works equally correctly for Serbian text too, even though
 * the locale tag says "hr" -- Serbian Latin script (as used throughout this
 * site's {@code .tab} files) uses the exact same Gaj's Latin alphabet and
 * letter order as Croatian; there's no separate "sr-Latn" collation needed
 * here, and Java has no such distinct tailoring to reach for regardless.
 */
public final class CroatianCollator {

    /**
     * Not a per-call {@code Collator.getInstance(...)} -- collator
     * construction does real, non-trivial setup work, and this instance is
     * safe to share: {@link Collator#compare(String, String)} does not
     * mutate any shared state between calls.
     */
    private static final Collator COLLATOR = Collator.getInstance(Locale.of("hr"));

    private CroatianCollator() {
    }

    /** For sorting artist/song-title strings -- see the class Javadoc. */
    public static Comparator<String> stringComparator() {
        return COLLATOR::compare;
    }

    /**
     * For sorting single-character map keys, e.g. {@link
     * com.vinoigitare.web.SongBrowseController}'s per-letter homepage
     * grouping -- {@link Collator} only compares {@link String}s, so this
     * just wraps each character as a one-character string rather than
     * asking every caller to do that themselves.
     */
    public static Comparator<Character> charComparator() {
        return (a, b) -> COLLATOR.compare(String.valueOf(a), String.valueOf(b));
    }
}
