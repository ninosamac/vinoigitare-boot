package com.vinoigitare.chords;

/**
 * One distinct chord used in a song, matched against {@link
 * ChordDiagramCatalog} -- for the song page's "chords used in this song"
 * list (issue #13). Deliberately smaller than {@link RenderedChordDiagram}
 * (no {@code svg} field): this list shows chord names + Play buttons only,
 * not fretboard diagrams -- {@code /chord-diagrams} is one click away for
 * anyone who wants the actual fingering.
 *
 * @param name     chord name exactly as it appears in the song's own text
 *                 (not re-cased or otherwise normalized) -- kept verbatim
 *                 so this list visually matches the chords/lyrics block
 *                 shown right above it on the song page (which also
 *                 always renders the untransposed text verbatim). {@code
 *                 SongBrowseController#songChordsFor} uses {@link
 *                 ChordTransposer#canonicalize} only internally, to find
 *                 the right catalog entry for an alternate spelling like
 *                 "Eb" or "Bb" -- never as this displayed name (a real bug
 *                 found 2026-07-22, Nino: showing the canonicalized
 *                 spelling here, e.g. "D#" for a song that plainly wrote
 *                 "Eb", didn't match what the song's own chords/lyrics
 *                 block above it showed)
 * @param fretsCsv comma-joined fret positions (see {@link
 *                 ChordDiagram#fretsCsv()}), for the Play button's
 *                 {@code data-frets} attribute
 */
public record SongChord(String name, String fretsCsv) {
}
