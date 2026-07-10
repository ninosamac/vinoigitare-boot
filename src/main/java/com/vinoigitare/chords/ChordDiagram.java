package com.vinoigitare.chords;

/**
 * A single guitar chord fingering, as a fretboard diagram would show it.
 *
 * @param name      chord name as displayed (e.g. {@code "C"}, {@code "Hm"})
 *                  -- German/ex-YU naming (H = English B-natural, B =
 *                  English B-flat), matching {@link ChordTransposer}'s
 *                  convention.
 * @param frets     one entry per string, low E to high E (index 0 = low
 *                  E, index 5 = high E): {@code -1} = muted, {@code 0} =
 *                  open, {@code N > 0} = fretted at fret N. Standard
 *                  open-position fingerings where one exists; otherwise
 *                  the common barre shape (Am-shape or Em-shape moved up
 *                  the neck), the same fingerings any beginner guitar
 *                  method teaches.
 * @param barreFret {@code 0} if this isn't a barre chord, otherwise the
 *                  fret the index finger bars, spanning from the
 *                  leftmost to the rightmost non-muted string. Explicit
 *                  rather than inferred from {@code frets} alone: a
 *                  chord can have multiple strings coincidentally
 *                  sharing their lowest fret without being a barre (open
 *                  D, for instance, has fret 2 on both the D and high-E
 *                  strings, fretted by two separate fingers) --
 *                  there's no way to tell "these strings happen to share
 *                  a fret" from "one finger is holding all of them down"
 *                  from the numbers alone.
 */
public record ChordDiagram(String name, int[] frets, int barreFret) {
}
