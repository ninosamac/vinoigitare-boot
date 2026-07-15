package com.vinoigitare.chords;

/**
 * A chord diagram already rendered to SVG markup, ready for a template --
 * shared between the on-screen {@code /chord-diagrams} page and the
 * personalized songbook PDF's chord-reference section, both of which need
 * the exact same (name, svg) pair per diagram.
 *
 * @param name chord name, for the template's label
 * @param svg  pre-rendered SVG markup for that chord
 */
public record RenderedChordDiagram(String name, String svg) {
}
