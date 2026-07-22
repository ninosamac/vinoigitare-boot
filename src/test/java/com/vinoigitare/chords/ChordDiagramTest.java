package com.vinoigitare.chords;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class ChordDiagramTest {

    @Test
    void fretsCsvJoinsFretsInStringOrderIncludingMutedStrings() {
        ChordDiagram c = new ChordDiagram("C", new int[] {-1, 3, 2, 0, 1, 0}, 0);

        assertThat(c.fretsCsv()).isEqualTo("-1,3,2,0,1,0");
    }

    @Test
    void fretsCsvHandlesDoubleDigitFrets() {
        ChordDiagram cSharp = new ChordDiagram("C#", new int[] {-1, 4, 6, 6, 6, 4}, 4, 4);

        assertThat(cSharp.fretsCsv()).isEqualTo("-1,4,6,6,6,4");
    }
}
