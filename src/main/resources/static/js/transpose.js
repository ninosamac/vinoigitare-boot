/**
 * Client-side chord transposition for the song page's -/+ buttons.
 *
 * This is an independent reimplementation of the same algorithm as
 * com.vinoigitare.chords.ChordTransposer (Java), used server-side by the
 * PDF download so a downloaded PDF matches whatever's on screen. The two
 * are NOT sharing literal code (Java vs. JavaScript can't), but they MUST
 * produce the same semitone-shift results for the same input -- see that
 * class's Javadoc for the full rationale (chord grammar, the H/B German
 * naming convention, and the chord-line detection heuristic). Keep the two
 * in sync if you change one.
 *
 * Design: transposition always recomputes from the ORIGINAL chords text
 * (kept in a data attribute), not incrementally from the currently
 * displayed text -- repeated +1/-1 clicks can't accumulate rounding or
 * regex-matching drift this way. The offset lives only in memory (reset on
 * page reload); it is NOT persisted to localStorage, unlike the font-size
 * and auto-scroll preferences (Phase 4d), because a transpose offset is
 * specific to a single song, not a general display preference.
 */
(function () {
    "use strict";

    // Chromatic scale; index 10 = "B" (German flat), index 11 = "H"
    // (German natural) -- mirrors ChordTransposer.NOTES exactly.
    var NOTES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "B", "H"];

    var NATURAL_INDEX = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 10, H: 11 };

    // Mirrors ChordTransposer.CHORD_PATTERN exactly (group numbers match).
    var CHORD_PATTERN = /^([A-H])(#|b)?((?:m|maj7|m7|7|sus[24]|dim|aug|add9|5|6|9|11|13)*)(?:\/([A-H])(#|b)?)?$/;

    function transposeRoot(letter, accidental, semitones) {
        var index = NATURAL_INDEX[letter];
        if (accidental === "#") {
            index += 1;
        } else if (accidental === "b") {
            index -= 1;
        }
        var transposed = ((index + semitones) % NOTES.length + NOTES.length) % NOTES.length;
        return NOTES[transposed];
    }

    function transposeChord(token, semitones) {
        var match = CHORD_PATTERN.exec(token);
        if (!match) {
            return token;
        }
        var root = transposeRoot(match[1], match[2], semitones);
        var suffix = match[3] || "";
        var bassRoot = match[4];
        if (!bassRoot) {
            return root + suffix;
        }
        var bass = transposeRoot(bassRoot, match[5], semitones);
        return root + suffix + "/" + bass;
    }

    function isChordLine(line) {
        var trimmed = line.trim();
        if (trimmed === "") {
            return false;
        }
        var tokens = trimmed.split(/\s+/);
        for (var i = 0; i < tokens.length; i++) {
            if (!CHORD_PATTERN.test(tokens[i])) {
                return false;
            }
        }
        return true;
    }

    function nonWhitespaceCount(line) {
        var count = 0;
        for (var i = 0; i < line.length; i++) {
            if (!/\s/.test(line.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    function neighborNonWhitespaceCountAt(lines, index) {
        if (index < 0 || index >= lines.length) {
            return null;
        }
        var candidate = lines[index];
        return candidate.trim() === "" ? null : nonWhitespaceCount(candidate);
    }

    function denserNeighborNonWhitespaceCount(lines, index) {
        var before = neighborNonWhitespaceCountAt(lines, index - 1);
        var after = neighborNonWhitespaceCountAt(lines, index + 1);
        if (before === null) {
            return after;
        }
        if (after === null) {
            return before;
        }
        return Math.max(before, after);
    }

    function isSparseRelativeToNeighbors(lines, index) {
        var neighborDensity = denserNeighborNonWhitespaceCount(lines, index);
        if (neighborDensity === null || neighborDensity === 0) {
            return true;
        }
        return nonWhitespaceCount(lines[index]) <= neighborDensity * 0.85;
    }

    function transposeChordLine(line, semitones) {
        return line.replace(/\S+|\s+/g, function (piece) {
            return /^\s+$/.test(piece) ? piece : transposeChord(piece, semitones);
        });
    }

    /** Transposes every chord line in `text` by `semitones`; exported for reuse/testing. */
    function transpose(text, semitones) {
        if (semitones === 0) {
            return text;
        }
        var lines = text.split("\n");
        for (var i = 0; i < lines.length; i++) {
            if (isChordLine(lines[i]) && isSparseRelativeToNeighbors(lines, i)) {
                lines[i] = transposeChordLine(lines[i], semitones);
            }
        }
        return lines.join("\n");
    }

    function initTransposeControls() {
        var chordsBlock = document.querySelector("[data-original-chords]");
        var downButton = document.querySelector("[data-transpose-down]");
        var upButton = document.querySelector("[data-transpose-up]");
        var display = document.querySelector("[data-transpose-display]");
        var pdfLink = document.querySelector("[data-pdf-link]");
        if (!chordsBlock || !downButton || !upButton) {
            return;
        }

        var originalChords = chordsBlock.getAttribute("data-original-chords");
        var offset = 0;

        function render() {
            chordsBlock.textContent = transpose(originalChords, offset);
            if (display) {
                display.textContent = offset > 0 ? "+" + offset : String(offset);
            }
            if (pdfLink) {
                var baseHref = pdfLink.getAttribute("data-pdf-base-href");
                pdfLink.setAttribute("href", offset === 0 ? baseHref : baseHref + "?transpose=" + offset);
            }
        }

        downButton.addEventListener("click", function () {
            offset -= 1;
            render();
        });
        upButton.addEventListener("click", function () {
            offset += 1;
            render();
        });
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initTransposeControls);
        } else {
            initTransposeControls();
        }
    }

    // Exposed for a Node-based sanity check against the same test cases as
    // ChordTransposerTest (see the migration notes) -- harmless in the
    // browser, where `module` is undefined.
    if (typeof module !== "undefined" && module.exports) {
        module.exports = { transpose: transpose, isChordLine: isChordLine };
    }
})();
