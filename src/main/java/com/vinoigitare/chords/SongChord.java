package com.vinoigitare.chords;

/**
 * One distinct chord used in a song, matched against {@link
 * ChordDiagramCatalog} -- for the song page's "chords used in this song"
 * list (issue #13). Deliberately smaller than {@link RenderedChordDiagram}
 * (no {@code svg} field): this list shows chord names + Play buttons only,
 * not fretboard diagrams -- {@code /chord-diagrams} is one click away for
 * anyone who wants the actual fingering.
 *
 * @param name     chord name as it appears in the song's own text (not
 *                 re-cased or otherwise normalized)
 * @param fretsCsv comma-joined fret positions (see {@link
 *                 ChordDiagram#fretsCsv()}), for the Play button's
 *                 {@code data-frets} attribute
 */
public record SongChord(String name, String fretsCsv) {
}
