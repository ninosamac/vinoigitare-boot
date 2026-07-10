package com.vinoigitare.chords;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class ChordTransposerTest {

    @Test
    void isChordLineTrueForAllChordTokens() {
        assertThat(ChordTransposer.isChordLine("C G Am F")).isTrue();
        assertThat(ChordTransposer.isChordLine("   Dm7   G7   ")).isTrue();
        assertThat(ChordTransposer.isChordLine("H B")).isTrue();
        assertThat(ChordTransposer.isChordLine("G/H Asus4")).isTrue();
    }

    @Test
    void isChordLineFalseForLyricsOrBlank() {
        assertThat(ChordTransposer.isChordLine("Ovo je tekst pesme")).isFalse();
        assertThat(ChordTransposer.isChordLine("")).isFalse();
        assertThat(ChordTransposer.isChordLine("   ")).isFalse();
        // "A" alone would match the chord grammar, but a whole line of prose
        // containing other non-chord words must not:
        assertThat(ChordTransposer.isChordLine("A quick brown fox")).isFalse();
    }

    @Test
    void transposeUpWrapsFromHToC() {
        assertThat(ChordTransposer.transpose("H", 1)).isEqualTo("C");
    }

    @Test
    void transposeDownWrapsFromCToH() {
        assertThat(ChordTransposer.transpose("C", -1)).isEqualTo("H");
    }

    @Test
    void germanBAndHAreOneSemitoneApart() {
        // German convention: B (flat) is one semitone below H (natural).
        assertThat(ChordTransposer.transpose("B", 1)).isEqualTo("H");
        assertThat(ChordTransposer.transpose("H", -1)).isEqualTo("B");
    }

    @Test
    void transposePreservesChordQualitySuffix() {
        // A is index 9; +2 semitones = index 11 = "H" (German naming: index
        // 10 is "B", index 11 is "H" -- see the class Javadoc).
        assertThat(ChordTransposer.transpose("Am7", 2)).isEqualTo("Hm7");
        assertThat(ChordTransposer.transpose("Gsus4", 1)).isEqualTo("G#sus4");
        assertThat(ChordTransposer.transpose("Cmaj7", 0)).isEqualTo("Cmaj7");
    }

    @Test
    void transposeHandlesSlashBassChordsIndependently() {
        // G/H up 2 semitones: G -> A, H -> C#.
        assertThat(ChordTransposer.transpose("G/H", 2)).isEqualTo("A/C#");
    }

    @Test
    void transposeHandlesSharpAndFlatAccidentals() {
        assertThat(ChordTransposer.transpose("C#", 1)).isEqualTo("D");
        assertThat(ChordTransposer.transpose("Db", -1)).isEqualTo("C");
    }

    @Test
    void transposeOnlyAffectsChordLinesNotLyrics() {
        // Built with explicit \n rather than a text block: text blocks
        // strip leading whitespace based on the *least-indented* line
        // across the whole block, which silently changes each line's raw
        // length relative to what's written in the source -- exactly the
        // metric isSparseRelativeToNeighbors cares about. Explicit
        // concatenation keeps the actual fixture content unambiguous.
        String chordLine1 = "   C          G           Am         F";
        String lyricLine1 = "Šašava priča o ljubavi i čežnji,";
        String chordLine2 = "   C          G           F     G";
        String lyricLine2 = "Đurđevak cveta, ćuti noć, žubori potok.";
        String text = chordLine1 + "\n" + lyricLine1 + "\n" + chordLine2 + "\n" + lyricLine2;

        String transposed = ChordTransposer.transpose(text, 2);
        List<String> lines = transposed.lines().toList();

        assertThat(lines.get(1)).isEqualTo(lyricLine1);
        assertThat(lines.get(3)).isEqualTo(lyricLine2);
        assertThat(lines.get(0)).isNotEqualTo(chordLine1);
        assertThat(lines.get(2)).isNotEqualTo(chordLine2);
        // C up 2 -> D, G up 2 -> A, Am up 2 -> Hm, F up 2 -> G.
        assertThat(lines.get(0)).contains("D").contains("A").contains("Hm").contains("G");
        assertThat(lines.get(2)).contains("D").contains("A").contains("G");
    }

    @Test
    void transposeByZeroReturnsTextUnchanged() {
        String text = "C G Am F\nSome lyrics here";
        assertThat(ChordTransposer.transpose(text, 0)).isEqualTo(text);
    }

    @Test
    void isChordLineTrueForHyphenatedFlatAlterationAndNumericWalkingBass() {
        // Real bug found in testing: a song added through the admin form
        // used these two notations, neither recognized by the original
        // grammar -- one unrecognized token failed the WHOLE line, so the
        // whole first paragraph's chord lines were misdetected as lyrics.
        // See CHORD_PATTERN's comment for the full explanation.
        assertThat(ChordTransposer.isChordLine("F#m7-5")).isTrue();
        assertThat(ChordTransposer.isChordLine("H7/3-4")).isTrue();
        assertThat(ChordTransposer.isChordLine("H7/4-3")).isTrue();
        assertThat(ChordTransposer.isChordLine("Em                C    Am   F#m7-5     H7/4-3")).isTrue();
        assertThat(ChordTransposer.isChordLine("Em          Am   D7                    G H7/3-4")).isTrue();
    }

    @Test
    void transposeShiftsTheRootOfAHyphenatedFlatAlterationChord() {
        // F# up 2 semitones = G# (index 6 + 2 = 8); the "m7-5" suffix is
        // carried through unchanged, same as any other quality suffix.
        assertThat(ChordTransposer.transpose("F#m7-5", 2)).isEqualTo("G#m7-5");
    }

    @Test
    void transposeCarriesTheNumericWalkingBassAnnotationThroughUnchanged() {
        // The "/3-4" is a scale-degree annotation, not a note -- it isn't
        // transposed, only the H7 root/quality before it is (H up 2 = C#).
        assertThat(ChordTransposer.transpose("H7/3-4", 2)).isEqualTo("C#7/3-4");
        assertThat(ChordTransposer.transpose("H7/4-3", 0)).isEqualTo("H7/4-3");
    }

    @Test
    void singleChordLineAboveAMuchLongerLyricLineStillTransposes() {
        // The common real case: a short chord line ("A") sitting above a
        // much longer lyric line must still be recognized as a chord line
        // by the relative-sparseness check and transposed. A (index 9) up
        // 1 semitone = index 10 = "B" (German naming).
        String text = "A\nThis is a much longer lyric line below it";
        String transposed = ChordTransposer.transpose(text, 1);
        assertThat(transposed).startsWith("B");
    }
}
