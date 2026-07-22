package com.vinoigitare.chords;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects chord lines in a chords/lyrics text blob and shifts every chord's
 * root (and bass note, for slash chords) by a number of semitones.
 *
 * <p><b>Chord grammar.</b> A single chord token looks like a root letter
 * (A-H, German/ex-YU convention -- see below), an optional accidental
 * (# or b), an optional quality suffix (m, maj7, m7, 7, sus2, sus4, dim,
 * aug, add9, 5, 6, 9, 11, 13, or a hyphenated flat alteration like -5/-9/
 * -11/-13, e.g. {@code F#m7-5}), and an optional slash-bass -- either a
 * real bass note (e.g. {@code G/H}, transposed along with the root) or a
 * bare numeric annotation (e.g. {@code H7/3-4}), which some real
 * songbooks use to mark a walking bass under an otherwise static chord;
 * that one is carried through verbatim on transpose rather than treated
 * as a note, since it isn't naming a pitch. This mirrors the plan's
 * suggested pattern:
 * {@code ^[A-HB](#|b)?(m|maj7|m7|7|sus[24]|dim|aug|add9|5|6|9|11|13)*
 * (/[A-HB](#|b)?)?$}, extended for the two real-world notations above
 * (found in an actual song added through the admin form -- see {@link
 * #CHORD_PATTERN}'s own comment for the exact tokens that motivated it).
 *
 * <p><b>H vs B.</b> The ex-YU corpus uses German note naming, where
 * {@code H} is what English calls B-natural and {@code B} is what English
 * calls B-flat -- they are two semitones apart from each other (one
 * semitone below H). Both are supported as independent root letters here
 * (see {@link #NOTES}, where index 10 is "B" and index 11 is "H").
 *
 * <p><b>Chord-line detection heuristic.</b> A line is treated as a chord
 * line if (a) every whitespace-separated token on it matches the chord
 * grammar above, and (b) where a neighboring non-blank line exists to
 * compare against, the line is noticeably shorter than it -- chords sit
 * sparsely above the lyrics they annotate. Condition (a) alone is already
 * fairly restrictive (an ordinary lyric word essentially never matches
 * this exact grammar), so (b) is a secondary corroborating signal, applied
 * only when there's something to compare against. <b>This is a heuristic,
 * not a guarantee</b> -- the migration plan flags chord-line detection as
 * the riskiest piece of this phase, and real-world {@code .tab} files with
 * unusual formatting may confuse it. It has been validated only against
 * this app's own fixtures.
 *
 * <p>The client-side mirror of this exact algorithm lives in {@code
 * src/main/resources/static/js/transpose.js}, for the interactive
 * on-screen transpose buttons; this Java version is what {@code
 * SongPdfController} applies before rendering a PDF, per the migration
 * plan (client-side JS keeps the server stateless for the interactive
 * view, but the PDF request is a single stateless HTTP call, so it's
 * transposed server-side instead).
 */
public final class ChordTransposer {

    /**
     * Chromatic scale, index 0 = C. Index 10 is "B" (German flat) and
     * index 11 is "H" (German natural) -- see the class Javadoc.
     */
    private static final String[] NOTES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "B", "H"
    };

    /** Natural (no accidental) chromatic index for each root letter A-H. */
    private static final java.util.Map<Character, Integer> NATURAL_INDEX = java.util.Map.of(
            'C', 0, 'D', 2, 'E', 4, 'F', 5, 'G', 7, 'A', 9, 'B', 10, 'H', 11);

    // Group 1: root letter. Group 2: optional accidental. Group 3: quality
    // suffix (the whole repeated-alternation match, since wrapping the
    // (?:...)* in its own capturing group captures the full matched
    // substring, not just the last repetition -- a Java-regex-specific
    // gotcha worth flagging for future maintainers). Group 4/5: optional
    // slash-bass root + accidental. Group 6: optional bare-numeric
    // walking-bass annotation (e.g. "/3-4") -- a real bug found in
    // testing: a song added through the admin form used "F#m7-5" (a
    // flat-5 alteration written with a hyphen instead of "b") and
    // "H7/3-4"/"H7/4-3" (walking bass under a static chord), neither of
    // which the original grammar recognized, so isChordLine rejected
    // those lines outright -- one unrecognized token failed the WHOLE
    // line, not just that token. "-5"/"-9"/"-11"/"-13" join the quality
    // alternation (group 3) to fix the first; group 6 is a second, entirely
    // separate optional slash-suffix to fix the second, deliberately NOT
    // reusing groups 4/5 -- transposeChord() transposes whatever those
    // capture as if it were a note letter, which a bare scale-degree
    // number isn't.
    private static final Pattern CHORD_PATTERN = Pattern.compile(
            "^([A-H])(#|b)?((?:m|maj7|m7|7|sus[24]|dim|aug|add9|5|6|9|11|13|-5|-9|-11|-13)*)"
                    + "(?:/([A-H])(#|b)?)?(/[0-9]+(?:-[0-9]+)?)?$");

    private ChordTransposer() {
    }

    /** True if every whitespace-separated token in {@code line} is a valid chord. */
    public static boolean isChordLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            if (!CHORD_PATTERN.matcher(token).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@code lines[index]} is a chord line by the full heuristic
     * (grammar match <b>and</b> sparse relative to neighbors) -- the exact
     * condition {@link #transpose(String, int)} uses to decide which lines
     * to shift. Exposed for {@code ChordLineHighlighter}, which needs the
     * identical decision (which lines are chord lines) for a different
     * purpose (visual styling instead of semitone shifting) -- keeping one
     * public entry point for "is this a chord line" means the two features
     * can't quietly disagree on which lines qualify.
     */
    public static boolean isHighlightableChordLine(String[] lines, int index) {
        return isChordLine(lines[index]) && isSparseRelativeToNeighbors(lines, index);
    }

    /**
     * Transposes every chord line in {@code text} by {@code semitones}
     * (positive = up, negative = down), leaving every other line
     * unchanged. Line endings and non-chord content are preserved exactly.
     */
    public static String transpose(String text, int semitones) {
        if (semitones == 0) {
            return text;
        }
        // Split on any line-ending style, not just "\n" -- see the identical
        // comment in ChordLineHighlighter.render() for why: admin-entered
        // chords come from a browser <textarea>, which submits CRLF, and a
        // stray trailing "\r" left on each line caused a doubled line break
        // (PDF rendering goes through this method too, so it shared the bug).
        String[] lines = text.split("\r\n|\r|\n", -1);
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isHighlightableChordLine(lines, i)) {
                result.append(transposeChordLine(line, semitones));
            } else {
                result.append(line);
            }
            if (i < lines.length - 1) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    /**
     * Every distinct chord token used in {@code text}'s chord lines, in
     * first-appearance order (a chord used more than once appears only
     * the one time) -- for the song page's "chords used in this song"
     * list (issue #13). Reuses the exact same chord-line detection
     * {@link #transpose(String, int)} uses, so this can never disagree
     * with it about which lines/tokens actually are chords.
     *
     * <p>Whether a returned token is one this app can actually play a
     * sound for (i.e. has a real, verified fingering in {@link
     * ChordDiagramCatalog}) is a separate question the caller answers by
     * looking each one up there -- some real songs use chord spellings
     * outside that curated set, and this method has no opinion on that;
     * it just reports what the song's own text says it uses.
     */
    public static List<String> distinctChordTokens(String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        Set<String> tokens = new LinkedHashSet<>();
        for (int i = 0; i < lines.length; i++) {
            if (!isHighlightableChordLine(lines, i)) {
                continue;
            }
            for (String token : lines[i].trim().split("\\s+")) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * Secondary corroborating signal: true if there's no usable neighbor to
     * compare against (can't disprove it), or the candidate line is
     * meaningfully sparser (&lt;= 85% of the *non-whitespace* character
     * count) than the denser of its immediate non-blank neighbors.
     *
     * <p>Deliberately compares non-whitespace character counts, not raw
     * line length: chord lines routinely have wide gaps between tokens to
     * sit above the right syllable, which can make their *raw* length
     * longer than a tightly-packed lyric line below them even though they
     * have far fewer actual characters. Raw length was tried first and
     * produced a false negative on exactly this pattern against this
     * project's own fixture data -- non-whitespace count is what the
     * "sparse" intuition actually means here.
     */
    private static boolean isSparseRelativeToNeighbors(String[] lines, int index) {
        Integer neighborDensity = denserNeighborNonWhitespaceCount(lines, index);
        if (neighborDensity == null || neighborDensity == 0) {
            return true;
        }
        return nonWhitespaceCount(lines[index]) <= neighborDensity * 0.85;
    }

    private static int nonWhitespaceCount(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static Integer neighborNonWhitespaceCountAt(String[] lines, int index) {
        if (index < 0 || index >= lines.length) {
            return null;
        }
        String candidate = lines[index];
        return candidate.trim().isEmpty() ? null : nonWhitespaceCount(candidate);
    }

    private static Integer denserNeighborNonWhitespaceCount(String[] lines, int index) {
        Integer before = neighborNonWhitespaceCountAt(lines, index - 1);
        Integer after = neighborNonWhitespaceCountAt(lines, index + 1);
        if (before == null) {
            return after;
        }
        if (after == null) {
            return before;
        }
        return Math.max(before, after);
    }

    /** Transposes every chord token on an already-confirmed chord line. */
    private static String transposeChordLine(String line, int semitones) {
        // Rebuild the line token-by-token, preserving the original
        // whitespace runs exactly so chord-above-lyric alignment survives.
        Matcher tokenMatcher = Pattern.compile("\\S+|\\s+").matcher(line);
        StringBuilder rebuilt = new StringBuilder(line.length());
        while (tokenMatcher.find()) {
            String piece = tokenMatcher.group();
            if (piece.isBlank()) {
                rebuilt.append(piece);
            } else {
                rebuilt.append(transposeChord(piece, semitones));
            }
        }
        return rebuilt.toString();
    }

    private static String transposeChord(String chord, int semitones) {
        Matcher matcher = CHORD_PATTERN.matcher(chord);
        if (!matcher.matches()) {
            return chord;
        }
        String root = transposeRoot(matcher.group(1), matcher.group(2), semitones);
        String suffix = matcher.group(3) == null ? "" : matcher.group(3);
        String bassRoot = matcher.group(4);
        // Group 6 (a bare-numeric walking-bass annotation, e.g. "/3-4") is
        // never transposed -- it isn't naming a pitch, so there's nothing
        // to shift -- just carried through verbatim, whichever of group
        // 4/6 (they're mutually exclusive in practice) is present.
        String numericAnnotation = matcher.group(6) == null ? "" : matcher.group(6);
        if (bassRoot == null) {
            return root + suffix + numericAnnotation;
        }
        String bass = transposeRoot(bassRoot, matcher.group(5), semitones);
        return root + suffix + "/" + bass + numericAnnotation;
    }

    private static String transposeRoot(String letter, String accidental, int semitones) {
        int index = NATURAL_INDEX.get(letter.charAt(0));
        if ("#".equals(accidental)) {
            index += 1;
        } else if ("b".equals(accidental)) {
            index -= 1;
        }
        int transposed = Math.floorMod(index + semitones, NOTES.length);
        return NOTES[transposed];
    }
}
