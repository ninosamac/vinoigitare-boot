package com.vinoigitare.chords;

/**
 * A chord diagram already rendered to SVG markup, ready for a template --
 * shared between the on-screen {@code /chord-diagrams} page and the
 * personalized songbook PDF's chord-reference section, both of which need
 * the exact same (name, svg) pair per diagram.
 *
 * @param name     chord name, for the template's label
 * @param svg      pre-rendered SVG markup for that chord
 * @param fretsCsv raw fret positions (see {@link ChordDiagram#frets()}),
 *                 comma-joined (e.g. {@code "0,2,2,1,0,0"}, {@code -1} for
 *                 muted) -- pre-formatted here in Java rather than in the
 *                 template, since Thymeleaf has no built-in expression for
 *                 turning an {@code int[]} into a delimited string and the
 *                 restricted-expression guard this fragment already lives
 *                 under (see {@code ChordDiagramRenderer}'s Javadoc) rules
 *                 out calling a helper method from inside the loop. Only
 *                 actually read by the on-screen page (issue #12, chord
 *                 playback via {@code static/js/chord-audio.js}) -- carried
 *                 here rather than forking this shared record into two
 *                 separate shapes, since the PDF path simply never reads
 *                 it.
 */
public record RenderedChordDiagram(String name, String svg, String fretsCsv) {
}
