package com.vinoigitare.model;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A song: an artist, a title, and the chords/lyrics text blob, plus the
 * fields Phase 4a's database backing added: a repository-specific id, a
 * URL slug, a creation timestamp, and a view counter. (Genre was added in
 * Phase 4c and removed again entirely on 2026-07-12 -- it was assigned
 * round-robin at import time purely so the public genre-browsing tab
 * (also since removed) had something in every category, never a real,
 * human-curated attribute.)
 *
 * <p>Ported from the old {@code Vinoigitare_Model} {@code Song} class. The
 * old class was a mutable Java 7 bean; here it's a record, since songs are
 * treated as immutable values in the new app (a rename becomes "store a new
 * Song", not "mutate the old one" -- see
 * {@code com.vinoigitare.web.AdminController#update}).
 *
 * <p><b>Two repositories, two id schemes, one type.</b> Phases 1-3 had a
 * single {@code TextFileSongRepository} where {@link #id()} was always the
 * derived string {@code "artist - title"} (also the storage filename
 * stem). Phase 4a introduces a database-backed repository with real
 * numeric ids and adds {@link #slug()}, used for the new
 * {@code /akordi/{id}/{slug}} URL scheme (matching pesmarica.rs). Rather
 * than fork the domain type in two, {@code id} stays a plain
 * {@code String} whose meaning is repository-specific: the database
 * repository stores the numeric id as a string; {@code
 * TextFileSongRepository} keeps using the legacy derived form. The 3-arg
 * constructor below reproduces the exact old behavior, so every Phase 1-3
 * caller (tests included) keeps working unchanged.
 *
 * @param id repository-specific identifier -- a numeric id (as a string)
 *           for database-backed songs, or the legacy {@code "artist -
 *           title"} derived string for file-backed songs. Never null after
 *           construction: the compact constructor derives it from
 *           artist/title if not supplied.
 * @param slug URL-friendly identifier, e.g. {@code
 *             "marko-markovic--probna-pesma"}, matching pesmarica.rs's own
 *             {@code Artist-Name--Song-Title} convention. Auto-derived
 *             from artist/title if not supplied.
 * @param createdAt nullable for file-backed songs, which have no real
 *                  creation timestamp; set for database-backed songs.
 * @param views view counter; 0 until Phase 4e wires up counting.
 */
public record Song(
        String id,
        String artist,
        String title,
        String slug,
        String chords,
        Instant createdAt,
        long views) {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC_RUN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_OR_TRAILING_HYPHENS = Pattern.compile("^-+|-+$");

    public Song {
        Objects.requireNonNull(artist, "artist must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(chords, "chords must not be null");
        if (slug == null || slug.isBlank()) {
            slug = slugify(artist) + "--" + slugify(title);
        }
        if (id == null) {
            id = artist + " - " + title;
        }
    }

    /**
     * Back-compat constructor matching the pre-Phase-4a shape: {@link #id()}
     * defaults to the legacy {@code "artist - title"} derived string (via
     * the compact constructor above), {@link #slug()} is auto-derived, and
     * createdAt/views take their empty defaults. Used by {@code
     * TextFileSongRepository}, existing tests, and admin-created songs
     * before they're persisted to the database (where the database
     * repository assigns the real numeric id).
     */
    public Song(String artist, String title, String chords) {
        this(null, artist, title, null, chords, null, 0L);
    }

    /**
     * Lowercase-ASCII, hyphenated slug, e.g. {@code "Šašava priča"} ->
     * {@code "sasava-prica"}. Serbian đ/Đ get an explicit substitution
     * first because (unlike š/č/ć/ž, which are accented Latin letters with
     * a real NFD decomposition) they have none -- see the equivalent note
     * in {@code com.vinoigitare.web.SongPdfController}, where the same gap
     * was found the same way.
     */
    private static String slugify(String text) {
        String withPlainD = text.replace('Đ', 'D').replace('đ', 'd');
        String decomposed = Normalizer.normalize(withPlainD, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String lower = withoutMarks.toLowerCase(Locale.ROOT);
        String hyphenated = NON_ALPHANUMERIC_RUN.matcher(lower).replaceAll("-");
        String trimmed = LEADING_OR_TRAILING_HYPHENS.matcher(hyphenated).replaceAll("");
        return trimmed.isEmpty() ? "song" : trimmed;
    }
}
