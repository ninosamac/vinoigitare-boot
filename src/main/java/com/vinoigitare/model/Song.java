package com.vinoigitare.model;

import java.util.Objects;

/**
 * A song: an artist, a title, and the chords/lyrics text blob.
 *
 * <p>Ported from the old {@code Vinoigitare_Model} {@code Song} class. The
 * old class was a mutable Java 7 bean; here it's a record, since songs are
 * treated as immutable values in the new app (a rename becomes "store a new
 * Song", not "mutate the old one" -- see
 * {@code com.vinoigitare.web.AdminController#update}).
 *
 * <p>{@link #id()} is derived exactly as before: {@code artist + " - " +
 * title}. This is also the filename stem used by
 * {@code com.vinoigitare.storage.TextFileSongRepository}. Known limitation
 * carried over from the old app: if either artist or title itself contains
 * the literal substring {@code " - "}, the id is still unambiguous to build
 * but parsing a filename back into (artist, title) is a best-effort split on
 * the first occurrence -- see {@code TextFileSongRepository} for details.
 */
public record Song(String artist, String title, String chords) {

    public Song {
        Objects.requireNonNull(artist, "artist must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(chords, "chords must not be null");
    }

    /**
     * Derived identifier, matching the old app's {@code "artist - title"}
     * scheme (used as the storage filename stem).
     */
    public String id() {
        return artist + " - " + title;
    }
}
