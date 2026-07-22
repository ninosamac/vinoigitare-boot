/**
 * Song page "chords used in this song" list (issue #13, 2026-07-22):
 * keeps the chord names shown next to each Play button in sync with the
 * transpose +/- buttons, entirely client-side.
 *
 * Reuses, rather than reimplements: transposeChord from transpose.js
 * (window.vinoigitareTranspose) for the actual semitone shift, and
 * chord-audio.js's own generic [data-chord-play] click binding for
 * playback itself (this file never calls playChord directly -- it only
 * ever updates a button's data-frets attribute, which chord-audio.js
 * reads fresh on every click).
 *
 * The whole chord catalog (name -> fretsCsv) is fetched once from
 * GET /chord-diagrams/catalog.json: after transposing, a chord's new name
 * might not be one this song originally used (e.g. G transposed +2
 * becomes A), so the lookup has to cover the full catalog, not just the
 * names the page started with.
 */
(function () {
    "use strict";

    var CATALOG_URL = "/chord-diagrams/catalog.json";

    function fetchCatalog() {
        return fetch(CATALOG_URL).then(function (response) {
            return response.json();
        }).then(function (entries) {
            var byName = {};
            entries.forEach(function (entry) {
                byName[entry.name] = entry.fretsCsv;
            });
            return byName;
        });
    }

    function currentOffset(chordsBlock) {
        var raw = chordsBlock.getAttribute("data-current-transpose");
        return raw ? parseInt(raw, 10) : 0;
    }

    /**
     * At offset 0, every row is restored to EXACTLY what the server
     * rendered (original spelling, e.g. "Eb", and its original
     * data-frets) -- no transposeChord call, no catalog lookup. Real bug
     * found 2026-07-22 (Nino, via two live song pages): always running
     * the original name through transposeChord (even at offset 0)
     * canonicalized it to this app's sharp-only spelling (e.g. "Eb" ->
     * "D#") immediately on page load, which no longer matched the
     * chords/lyrics block right above it -- that block always renders
     * the untransposed text verbatim at offset 0, so this list must too.
     * Captured once at init, before any transpose click, specifically so
     * a later click back to offset 0 (e.g. +1 then -1) can restore this
     * exact original state, not just skip re-transposing from whatever's
     * currently displayed.
     */
    function originalStateOf(rows) {
        return rows.map(function (row) {
            return {
                name: row.getAttribute("data-original-name"),
                frets: row.querySelector("[data-chord-play]").getAttribute("data-frets")
            };
        });
    }

    /**
     * Transposes each row's ORIGINAL chord name by `offset`, looks the
     * result up in the fetched catalog, and updates the row's visible
     * name + Play button's data-frets -- or hides the row if the
     * transposed name isn't in the catalog (rare, but possible; same "no
     * fake data" principle as the rest of this feature, applied per-row
     * instead of only at page load). At offset 0, restores the pristine
     * original instead (see originalStateOf's comment).
     */
    function updateRows(rows, originals, byName, offset) {
        var transposeChord = window.vinoigitareTranspose && window.vinoigitareTranspose.transposeChord;
        rows.forEach(function (row, i) {
            var nameEl = row.querySelector("[data-song-chord-name]");
            var playButton = row.querySelector("[data-chord-play]");
            if (offset === 0) {
                row.style.display = "";
                if (nameEl) {
                    nameEl.textContent = originals[i].name;
                }
                if (playButton) {
                    playButton.setAttribute("data-frets", originals[i].frets);
                }
                return;
            }
            var name = transposeChord ? transposeChord(originals[i].name, offset) : originals[i].name;
            var fretsCsv = byName[name];
            if (!fretsCsv) {
                row.style.display = "none";
                return;
            }
            row.style.display = "";
            if (nameEl) {
                nameEl.textContent = name;
            }
            if (playButton) {
                playButton.setAttribute("data-frets", fretsCsv);
            }
        });
    }

    function initSongChords() {
        var section = document.querySelector("[data-song-chords]");
        var chordsBlock = document.querySelector("[data-original-chords]");
        if (!section || !chordsBlock) {
            return;
        }
        var rows = Array.prototype.slice.call(section.querySelectorAll("[data-song-chord-row]"));
        if (rows.length === 0) {
            return;
        }
        var originals = originalStateOf(rows);

        fetchCatalog().then(function (byName) {
            updateRows(rows, originals, byName, currentOffset(chordsBlock));

            // Registered after transpose.js's own listeners on these same
            // buttons (song.html loads this script after transpose.js), so
            // data-current-transpose already reflects the new offset by
            // the time these fire.
            var downButton = document.querySelector("[data-transpose-down]");
            var upButton = document.querySelector("[data-transpose-up]");
            if (downButton) {
                downButton.addEventListener("click", function () {
                    updateRows(rows, originals, byName, currentOffset(chordsBlock));
                });
            }
            if (upButton) {
                upButton.addEventListener("click", function () {
                    updateRows(rows, originals, byName, currentOffset(chordsBlock));
                });
            }
        });
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initSongChords);
        } else {
            initSongChords();
        }
    }
})();
