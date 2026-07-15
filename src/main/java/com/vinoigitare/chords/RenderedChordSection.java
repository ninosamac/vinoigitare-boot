package com.vinoigitare.chords;

import java.util.List;

/**
 * A {@link ChordDiagramCatalog} section with its diagrams already rendered
 * to SVG -- see {@link RenderedChordDiagram}'s Javadoc for why this is
 * shared rather than living in just one controller.
 *
 * @param labelKey message-bundle key for the section heading, resolved in the template via {@code #{...}}
 */
public record RenderedChordSection(String labelKey, List<RenderedChordDiagram> diagrams) {
}
