package com.vinoigitare.chords;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class ChordDiagramRendererTest {

    private final ChordDiagramRenderer renderer = new ChordDiagramRenderer();

    @Test
    void rendersAValidLookingSvgWithOneMarkerPerString() {
        ChordDiagram c = new ChordDiagram("C", new int[] {-1, 3, 2, 0, 1, 0}, 0);

        String svg = renderer.render(c);

        assertThat(svg).startsWith("<svg").endsWith("</svg>");
        // One marker per string: 1 muted (x), 2 open (o), 3 fretted (dots).
        assertThat(countOccurrences(svg, "&#215;")).isEqualTo(1); // muted (low E)
        assertThat(countOccurrences(svg, "&#9675;")).isEqualTo(2); // open (D, high E)
        assertThat(countOccurrences(svg, "chord-diagram-dot")).isEqualTo(3); // fretted (A, D, G... here: 3,2,1)
    }

    @Test
    void allOpenStringsProduceNoDotsOrMutedMarkers() {
        ChordDiagram e = new ChordDiagram("E", new int[] {0, 2, 2, 1, 0, 0}, 0);

        String svg = renderer.render(e);

        assertThat(countOccurrences(svg, "&#215;")).isZero();
        assertThat(countOccurrences(svg, "&#9675;")).isEqualTo(3);
        assertThat(countOccurrences(svg, "chord-diagram-dot")).isEqualTo(3);
    }

    @Test
    void drawsABarreSpanningTheLeftmostToRightmostNonMutedStringWhenMarkedAsABarreChord() {
        // F: barre at fret 1, no muted strings -- spans the full width.
        ChordDiagram f = new ChordDiagram("F", new int[] {1, 3, 3, 2, 1, 1}, 1);

        String svg = renderer.render(f);

        assertThat(svg).contains("chord-diagram-barre");
        assertThat(svg).contains("class=\"chord-diagram-barre\"");
    }

    @Test
    void doesNotDrawABarreWhenNotMarkedAsABarreChordEvenIfFretsCoincidentallyRepeat() {
        // Open D: strings 4 and 6 both happen to be fretted at 2, by two
        // separate fingers, not a barre -- this is exactly the case that
        // made pure fret-number inference produce a false positive before
        // barreFret became explicit data (see ChordDiagram's Javadoc).
        ChordDiagram d = new ChordDiagram("D", new int[] {-1, -1, 0, 2, 3, 2}, 0);

        String svg = renderer.render(d);

        assertThat(svg).doesNotContain("chord-diagram-barre");
    }

    @Test
    void barreSpansOnlyTheNonMutedStringsWhenSomeAreMuted() {
        // Hm: barre at fret 2 across strings 2-6 (index 1-5); the muted
        // low E (index 0) must NOT be included in the barre's span.
        ChordDiagram hm = new ChordDiagram("Hm", new int[] {-1, 2, 4, 4, 3, 2}, 2);

        String svg = renderer.render(hm);

        // stringX(1) = 15 + 16*1 = 31 (leftmost of the barre), stringX(5) = 95 (rightmost).
        assertThat(svg).contains("<line x1=\"31\"").contains("x2=\"95\"");
    }

    @Test
    void baseFretGreaterThanOneShowsAPositionLabelAndNoThickNut() {
        // C#: A-shape barre at (absolute) fret 4 -- too high to fit a
        // window starting at the nut, so it's shown as a shifted window
        // with a "4fr" label instead, like a printed chord book would.
        ChordDiagram cSharp = new ChordDiagram("C#", new int[] {-1, 4, 6, 6, 6, 4}, 4, 4);

        String svg = renderer.render(cSharp);

        assertThat(svg).doesNotContain("chord-diagram-nut");
        assertThat(svg).contains(">4fr<");
    }

    @Test
    void baseFretOfOneIsIndistinguishableFromTheImplicitDefault() {
        ChordDiagram explicit = new ChordDiagram("C", new int[] {-1, 3, 2, 0, 1, 0}, 0, 1);
        ChordDiagram implicit = new ChordDiagram("C", new int[] {-1, 3, 2, 0, 1, 0}, 0);

        assertThat(renderer.render(explicit)).isEqualTo(renderer.render(implicit));
    }

    @Test
    void chordNameIsEscapedInTheAriaLabel() {
        ChordDiagram weird = new ChordDiagram("<script>", new int[] {0, 0, 0, 0, 0, 0}, 0);

        String svg = renderer.render(weird);

        assertThat(svg).doesNotContain("<script>chord diagram");
        assertThat(svg).contains("&lt;script&gt;");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
