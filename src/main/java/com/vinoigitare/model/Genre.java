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

    POP_ROCK("pop-rock", "Pop/Rock"),
    NARODNO("narodno", "Narodno"),
    STRANO("strano", "Strano");

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
}
