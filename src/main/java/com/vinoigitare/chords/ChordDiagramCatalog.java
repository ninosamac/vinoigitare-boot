package com.vinoigitare.chords;

import java.util.List;

/**
 * The reference set of chord diagrams shown on the chord-diagram page
 * (Phase 7's "chord-diagram reference page", per the migration plan and
 * pesmarica.rs's own {@code /dijagramiakorda}).
 *
 * <p>Grouped into {@link Section}s (major, minor, dominant 7th, and so
 * on) rather than one flat list, since the catalog covers a lot of
 * ground: all 12 roots (German/ex-YU naming, matching {@link
 * ChordTransposer}: C C# D D# E F F# G G# A B H) as both major and
 * minor, dominant 7th/major 7th/minor 7th/sus4/add9/diminished/augmented
 * for the seven natural roots, and sus2 and add9 wherever a clean,
 * playable voicing exists without an inaccurate guitar-mechanics
 * shortcut (see the per-chord comments below for the handful of
 * naturals skipped for exactly that reason).
 *
 * <p>Every fingering here was derived and checked against standard
 * guitar tuning (low-to-high E A D G B E) note by note -- root, third,
 * fifth, and any extension all confirmed to land on the intended pitch
 * class at that string and fret -- rather than recalled from memory,
 * since a wrong finger position here would actively teach someone the
 * wrong chord. Where a natural has a well-known open-position shape,
 * that's what's used; sharps/flats and anything needing a barre use the
 * standard movable E-shape (root on the low E string) or A-shape (root
 * on the A string) family any beginner method teaches -- not this app's
 * own invention. Sharp/flat roots and a few of the natural-root barre
 * voicings land above fret 5, so they're shown with an explicit {@link
 * ChordDiagram#baseFret()} and a "Nfr" position label, same as a printed
 * chord book would.
 */
public final class ChordDiagramCatalog {

    private ChordDiagramCatalog() {
    }

    /** A labeled group of chords, in display order. {@code labelKey} is a message-bundle key. */
    public record Section(String labelKey, List<ChordDiagram> chords) {
    }

    public static List<Section> sections() {
        return List.of(
                new Section("chordDiagrams.section.major", majors()),
                new Section("chordDiagrams.section.minor", minors()),
                new Section("chordDiagrams.section.dominant7", dominant7()),
                new Section("chordDiagrams.section.major7", major7()),
                new Section("chordDiagrams.section.minor7", minor7()),
                new Section("chordDiagrams.section.sus4", sus4()),
                new Section("chordDiagrams.section.sus2", sus2()),
                new Section("chordDiagrams.section.add9", add9()),
                new Section("chordDiagrams.section.diminished", diminished()),
                new Section("chordDiagrams.section.augmented", augmented()));
    }

    /** Every chord in the catalog, flattened -- for callers that don't care about grouping. */
    public static List<ChordDiagram> all() {
        return sections().stream().flatMap(section -> section.chords().stream()).toList();
    }

    /**
     * All 12 roots, open-position where one exists (C D E F G A H),
     * otherwise the standard E-shape or A-shape barre (see class
     * Javadoc). Sharps land on whichever of the two shape families gives
     * the lowest, most commonly taught position -- e.g. C# as an A-shape
     * barre at fret 4, not an E-shape barre at fret 9.
     */
    private static List<ChordDiagram> majors() {
        return List.of(
                new ChordDiagram("C", new int[] {-1, 3, 2, 0, 1, 0}, 0),
                new ChordDiagram("C#", new int[] {-1, 4, 6, 6, 6, 4}, 4, 4),
                new ChordDiagram("D", new int[] {-1, -1, 0, 2, 3, 2}, 0),
                new ChordDiagram("D#", new int[] {-1, 6, 8, 8, 8, 6}, 6, 6),
                new ChordDiagram("E", new int[] {0, 2, 2, 1, 0, 0}, 0),
                new ChordDiagram("F", new int[] {1, 3, 3, 2, 1, 1}, 1),
                new ChordDiagram("F#", new int[] {2, 4, 4, 3, 2, 2}, 2, 2),
                new ChordDiagram("G", new int[] {3, 2, 0, 0, 0, 3}, 0),
                new ChordDiagram("G#", new int[] {4, 6, 6, 5, 4, 4}, 4, 4),
                new ChordDiagram("A", new int[] {-1, 0, 2, 2, 2, 0}, 0),
                new ChordDiagram("B", new int[] {-1, 1, 3, 3, 3, 1}, 1, 1),
                new ChordDiagram("H", new int[] {-1, 2, 4, 4, 4, 2}, 2));
    }

    private static List<ChordDiagram> minors() {
        return List.of(
                new ChordDiagram("Cm", new int[] {-1, 3, 5, 5, 4, 3}, 3),
                new ChordDiagram("C#m", new int[] {-1, 4, 6, 6, 5, 4}, 4, 4),
                new ChordDiagram("Dm", new int[] {-1, -1, 0, 2, 3, 1}, 0),
                new ChordDiagram("D#m", new int[] {-1, 6, 8, 8, 7, 6}, 6, 6),
                new ChordDiagram("Em", new int[] {0, 2, 2, 0, 0, 0}, 0),
                new ChordDiagram("Fm", new int[] {1, 3, 3, 1, 1, 1}, 1),
                new ChordDiagram("F#m", new int[] {2, 4, 4, 2, 2, 2}, 2, 2),
                new ChordDiagram("Gm", new int[] {3, 5, 5, 3, 3, 3}, 3),
                new ChordDiagram("G#m", new int[] {4, 6, 6, 4, 4, 4}, 4, 4),
                new ChordDiagram("Am", new int[] {-1, 0, 2, 2, 1, 0}, 0),
                new ChordDiagram("Bm", new int[] {-1, 1, 3, 3, 2, 1}, 1, 1),
                new ChordDiagram("Hm", new int[] {-1, 2, 4, 4, 3, 2}, 2));
    }

    /** Dominant 7ths for the seven naturals -- extremely common in this corpus's folk/pop songs. */
    private static List<ChordDiagram> dominant7() {
        return List.of(
                new ChordDiagram("C7", new int[] {-1, 3, 2, 3, 1, 0}, 0),
                new ChordDiagram("D7", new int[] {-1, -1, 0, 2, 1, 2}, 0),
                new ChordDiagram("E7", new int[] {0, 2, 0, 1, 0, 0}, 0),
                new ChordDiagram("F7", new int[] {1, 3, 1, 2, 1, 1}, 1),
                new ChordDiagram("G7", new int[] {3, 2, 0, 0, 0, 1}, 0),
                new ChordDiagram("A7", new int[] {-1, 0, 2, 0, 2, 0}, 0),
                new ChordDiagram("H7", new int[] {-1, 2, 1, 2, 0, 2}, 0));
    }

    private static List<ChordDiagram> major7() {
        return List.of(
                new ChordDiagram("Cmaj7", new int[] {-1, 3, 2, 0, 0, 0}, 0),
                new ChordDiagram("Dmaj7", new int[] {-1, -1, 0, 2, 2, 2}, 0),
                new ChordDiagram("Emaj7", new int[] {0, 2, 1, 1, 0, 0}, 0),
                new ChordDiagram("Fmaj7", new int[] {-1, -1, 3, 2, 1, 0}, 0),
                new ChordDiagram("Gmaj7", new int[] {3, 2, 0, 0, 0, 2}, 0),
                new ChordDiagram("Amaj7", new int[] {-1, 0, 2, 1, 2, 0}, 0),
                new ChordDiagram("Hmaj7", new int[] {-1, 2, 4, 3, 4, 2}, 2));
    }

    private static List<ChordDiagram> minor7() {
        return List.of(
                new ChordDiagram("Cm7", new int[] {-1, 3, 5, 3, 4, 3}, 3),
                new ChordDiagram("Dm7", new int[] {-1, -1, 0, 2, 1, 1}, 0),
                new ChordDiagram("Em7", new int[] {0, 2, 0, 0, 0, 0}, 0),
                new ChordDiagram("Fm7", new int[] {1, 3, 1, 1, 1, 1}, 1),
                new ChordDiagram("Gm7", new int[] {3, 5, 3, 3, 3, 3}, 3),
                new ChordDiagram("Am7", new int[] {-1, 0, 2, 0, 1, 0}, 0),
                new ChordDiagram("Hm7", new int[] {-1, 2, 4, 2, 3, 2}, 2));
    }

    private static List<ChordDiagram> sus4() {
        return List.of(
                new ChordDiagram("Csus4", new int[] {-1, 3, 3, 0, 1, 1}, 0),
                new ChordDiagram("Dsus4", new int[] {-1, -1, 0, 2, 3, 3}, 0),
                new ChordDiagram("Esus4", new int[] {0, 2, 2, 2, 0, 0}, 0),
                new ChordDiagram("Fsus4", new int[] {1, 3, 3, 3, 1, 1}, 1),
                new ChordDiagram("Gsus4", new int[] {3, 3, 0, 0, 1, 3}, 0),
                new ChordDiagram("Asus4", new int[] {-1, 0, 2, 2, 3, 0}, 0),
                new ChordDiagram("Hsus4", new int[] {-1, 2, 4, 4, 5, 2}, 2));
    }

    /**
     * Esus2 and Fsus2 are deliberately not included: getting the 2nd onto
     * an open shape for those two roots without either losing the 2nd
     * entirely or requiring a physically awkward finger-lifted-off-the-
     * barre technique isn't a clean, standard voicing, so rather than
     * guess at one, those two are left out.
     */
    private static List<ChordDiagram> sus2() {
        return List.of(
                new ChordDiagram("Csus2", new int[] {-1, 3, 0, 0, 1, -1}, 0),
                new ChordDiagram("Dsus2", new int[] {-1, -1, 0, 2, 3, 0}, 0),
                new ChordDiagram("Gsus2", new int[] {3, 0, 0, 0, -1, 3}, 0),
                new ChordDiagram("Asus2", new int[] {-1, 0, 2, 2, 0, 0}, 0),
                new ChordDiagram("Hsus2", new int[] {-1, 2, 4, 4, 2, 2}, 2));
    }

    /**
     * Fadd9 and Hadd9 are deliberately not included, for the same reason
     * as the sus2 gaps above: the only fingerings that land on the right
     * notes for those two roots require a fourth non-barre finger (more
     * than a hand has) or an awkward reach, so they're left out rather
     * than shown as something nobody could actually play.
     */
    private static List<ChordDiagram> add9() {
        return List.of(
                new ChordDiagram("Cadd9", new int[] {-1, 3, 2, 0, 3, 0}, 0),
                new ChordDiagram("Dadd9", new int[] {-1, -1, 2, 2, 3, 2}, 0),
                new ChordDiagram("Eadd9", new int[] {0, 2, 2, 1, 0, 2}, 0),
                new ChordDiagram("Gadd9", new int[] {3, 2, 0, 0, 0, 5}, 0),
                new ChordDiagram("Aadd9", new int[] {-1, 2, 2, 2, 2, 0}, 0));
    }

    /**
     * Fully diminished 7th voicings (what songbooks mean by e.g. "Cdim"
     * in practice) for the seven naturals, built directly from the
     * stacked-minor-thirds interval formula (root, minor 3rd, diminished
     * 5th, diminished 7th) rather than a single movable shape reused
     * across roots -- a plain shape-shift by string can silently drift
     * onto the wrong four notes for a symmetric chord like this one, so
     * each root's fingering was derived and verified independently.
     */
    private static List<ChordDiagram> diminished() {
        return List.of(
                new ChordDiagram("Cdim", new int[] {2, 3, 1, 2, -1, -1}, 0),
                new ChordDiagram("Ddim", new int[] {4, 5, 3, 4, -1, -1}, 0),
                new ChordDiagram("Edim", new int[] {0, -1, 5, 3, 2, -1}, 0),
                new ChordDiagram("Fdim", new int[] {1, 2, -1, 1, 3, -1}, 0),
                new ChordDiagram("Gdim", new int[] {3, 1, 2, -1, 2, -1}, 0),
                new ChordDiagram("Adim", new int[] {2, 0, -1, 5, 4, -1}, 0),
                new ChordDiagram("Hdim", new int[] {1, 2, 0, 1, -1, -1}, 0));
    }

    /** Augmented triads (root, major 3rd, augmented 5th) for the seven naturals. */
    private static List<ChordDiagram> augmented() {
        return List.of(
                new ChordDiagram("Caug", new int[] {-1, 3, 2, 1, -1, 0}, 0),
                new ChordDiagram("Daug", new int[] {-1, 1, 0, -1, -1, 2}, 0),
                new ChordDiagram("Eaug", new int[] {0, 3, -1, 1, -1, -1}, 0),
                new ChordDiagram("Faug", new int[] {1, 4, -1, 2, -1, -1}, 0),
                new ChordDiagram("Gaug", new int[] {3, -1, -1, 4, 4, -1}, 0),
                new ChordDiagram("Aaug", new int[] {1, 0, -1, -1, 2, -1}, 0),
                new ChordDiagram("Haug", new int[] {-1, 2, 1, 0, -1, -1}, 0));
    }
}
