package com.vinoigitare.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The three pesmarica.rs-style genre categories (Phase 4c). {@link
 * Song#genre()} itself stays a plain nullable {@code String} -- storing
 * the {@linkplain #label() display label} directly (e.g. {@code
 * "Pop/Rock"}) -- so existing songs without a genre assigned don't break
 * anything. This enum exists to give the admin form a fixed dropdown and
 * the genre-browsing controller a canonical, iterable list, and to
 * provide a URL-safe {@linkplain #slug() slug} since {@code "Pop/Rock"}'s
 * literal {@code "/"} can't be a path segment.
 */
public enum Genre {

    // Slugs stay as-is (URL/DB-stable, already matched pesmarica.rs's own
    // scheme); labels translated to English as part of the site-wide
    // switch away from Serbian UI text. "Narodno"/"Strano" don't have a
    // single universally "correct" English equivalent for these Balkan
    // radio-style genre categories -- "Folk"/"Foreign" is a reasonable,
    // simple choice, not a claim of perfect equivalence.
    POP_ROCK("pop-rock", "Pop/Rock"),
    NARODNO("narodno", "Folk"),
    STRANO("strano", "Foreign");

    private final String slug;
    private final String label;

    Genre(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public static Optional<Genre> fromSlug(String slug) {
        return Arrays.stream(values()).filter(genre -> genre.slug.equals(slug)).findFirst();
    }

    /**
     * Resolves a raw {@link com.vinoigitare.model.Song#genre()} value to
     * its canonical {@code Genre}, tolerant of every form that value has
     * ever taken on disk: the slug (what {@code AdminController} stores
     * going forward), the current English label (what it stored before
     * that), or the original Serbian label text {@code SongImporter}
     * assigned before the site-wide English i18n switch -- e.g. a song
     * imported back then still has the literal text {@code "Strano"}
     * sitting in its genre column.
     *
     * <p>A real bug found in testing: without this, a song edited after
     * that switch ended up with genre {@code "Foreign"} while an
     * untouched older song still had {@code "Strano"} -- two different
     * strings for what's the same category, so genre browsing and the
     * admin list silently treated them as different genres. Case-
     * insensitive slug matching alone happens to cover the old Serbian
     * text too, since e.g. {@code "Strano"} and the {@code "strano"}
     * slug differ only in case -- no separate legacy-label table needed.
     * Callers that display the result should show {@link #label()}, not
     * echo the raw stored value, so old and new rows read identically
     * regardless of which form is still on disk; the value only actually
     * normalizes to the slug once that row is next saved through the
     * admin form.
     */
    public static Optional<Genre> resolve(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(genre -> genre.slug.equalsIgnoreCase(storedValue) || genre.label.equalsIgnoreCase(storedValue))
                .findFirst();
    }
}
