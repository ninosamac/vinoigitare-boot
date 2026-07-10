package com.vinoigitare.chords;

import java.util.List;

/**
 * The reference set of chord diagrams shown on the chord-diagram page
 * (Phase 7's "chord-diagram reference page", per the migration plan and
 * pesmarica.rs's own {@code /dijagramiakorda}). Standard open-position
 * fingerings where one exists (C, D, E, G, A, Am, Dm, Em); the common
 * barre shape otherwise (Am-shape or Em-shape moved up the neck) -- the
 * same fingerings any beginner guitar method teaches, not this app's own
 * invention.
 *
 * <p>Covers the seven natural roots (German/ex-YU naming, matching
 * {@link ChordTransposer}: C D E F G A H) in both major and minor, since
 * those are what a beginner actually needs first and what most songs in
 * this corpus are built from. Sharps/flats are a natural follow-up, not
 * done here.
 */
public final class ChordDiagramCatalog {

    private ChordDiagramCatalog() {
    }

    public static List<ChordDiagram> all() {
        return List.of(
                new ChordDiagram("C", new int[] {-1, 3, 2, 0, 1, 0}, 0),
                new ChordDiagram("D", new int[] {-1, -1, 0, 2, 3, 2}, 0),
                new ChordDiagram("E", new int[] {0, 2, 2, 1, 0, 0}, 0),
                new ChordDiagram("F", new int[] {1, 3, 3, 2, 1, 1}, 1),
                new ChordDiagram("G", new int[] {3, 2, 0, 0, 0, 3}, 0),
                new ChordDiagram("A", new int[] {-1, 0, 2, 2, 2, 0}, 0),
                new ChordDiagram("H", new int[] {-1, 2, 4, 4, 4, 2}, 2),
                new ChordDiagram("Cm", new int[] {-1, 3, 5, 5, 4, 3}, 3),
                new ChordDiagram("Dm", new int[] {-1, -1, 0, 2, 3, 1}, 0),
                new ChordDiagram("Em", new int[] {0, 2, 2, 0, 0, 0}, 0),
                new ChordDiagram("Fm", new int[] {1, 3, 3, 1, 1, 1}, 1),
                new ChordDiagram("Gm", new int[] {3, 5, 5, 3, 3, 3}, 3),
                new ChordDiagram("Am", new int[] {-1, 0, 2, 2, 1, 0}, 0),
                new ChordDiagram("Hm", new int[] {-1, 2, 4, 4, 3, 2}, 2));
    }
}
