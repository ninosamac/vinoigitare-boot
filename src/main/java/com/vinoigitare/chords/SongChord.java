package com.vinoigitare.chords;

/**
 * One distinct chord used in a song, matched against {@link
 * ChordDiagramCatalog} -- for the song page's "chords used in this song"
 * list (issue #13). Deliberately smaller than {@link RenderedChordDiagram}
 * (no {@code svg} field): this list shows chord names + Play buttons only,
 * not fretboard diagrams -- {@code /chord-diagrams} is one click away for
 * anyone who wants the actual fingering.
 *
 * @param name     the catalog's own canonical spelling of this chord (see
 *                 {@link ChordDiagramCatalog}) -- not necessarily verbatim
 *                 from the song's own text, since {@code
 *                 SongBrowseController#songChordsFor} normalizes an
 *                 alternate spelling like "Bb" to this convention's own
 *                 "B" (same pitch) before matching it against the
 *                 catalog, so the row shown is consistent with the rest
 *                 of the site's own note-naming convention
 * @param fretsCsv comma-joined fret positions (see {@link
 *                 ChordDiagram#fretsCsv()}), for the Play button's
 *                 {@code data-frets} attribute
 */
public record SongChord(String name, String fretsCsv) {
}
