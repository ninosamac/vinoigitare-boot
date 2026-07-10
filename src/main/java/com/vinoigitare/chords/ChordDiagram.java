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
 *                  open, {@code N > 0} = fretted at absolute fret N (not
 *                  relative to {@code baseFret} -- see that field).
 *                  Standard open-position fingerings where one exists;
 *                  otherwise the common barre shape (Am-shape or Em-shape
 *                  moved up the neck), the same fingerings any beginner
 *                  guitar method teaches.
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
 * @param baseFret  {@code 1} for a diagram anchored at the nut (the
 *                  common case -- every chord before the chromatic/7th/
 *                  extended-chord expansion used this implicitly).
 *                  {@code N > 1} shows a 5-fret window starting at fret
 *                  N instead, with a small "Nfr" position label, exactly
 *                  like a printed chord book does for a shape that
 *                  doesn't start at the nut -- needed once the catalog
 *                  covers roots like C# or D#, whose lowest practical
 *                  barre-chord fingering sits well past fret 5.
 */
public record ChordDiagram(String name, int[] frets, int barreFret, int baseFret) {

    public ChordDiagram(String name, int[] frets, int barreFret) {
        this(name, frets, barreFret, 1);
    }
}
