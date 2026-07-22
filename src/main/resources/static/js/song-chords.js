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
     * Transposes each row's ORIGINAL chord name (kept in a data attribute,
     * never overwritten) by `offset`, looks the result up in the fetched
     * catalog, and updates the row's visible name + Play button's
     * data-frets -- or hides the row if the transposed name isn't in the
     * catalog (rare, but possible; same "no fake data" principle as the
     * rest of this feature, applied per-row instead of only at page load).
     */
    function updateRows(rows, byName, offset) {
        var transposeChord = window.vinoigitareTranspose && window.vinoigitareTranspose.transposeChord;
        rows.forEach(function (row) {
            var originalName = row.getAttribute("data-original-name");
            var name = transposeChord ? transposeChord(originalName, offset) : originalName;
            var fretsCsv = byName[name];
            if (!fretsCsv) {
                row.style.display = "none";
                return;
            }
            row.style.display = "";
            var nameEl = row.querySelector("[data-song-chord-name]");
            if (nameEl) {
                nameEl.textContent = name;
            }
            var playButton = row.querySelector("[data-chord-play]");
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

        fetchCatalog().then(function (byName) {
            updateRows(rows, byName, currentOffset(chordsBlock));

            // Registered after transpose.js's own listeners on these same
            // buttons (song.html loads this script after transpose.js), so
            // data-current-transpose already reflects the new offset by
            // the time these fire.
            var downButton = document.querySelector("[data-transpose-down]");
            var upButton = document.querySelector("[data-transpose-up]");
            if (downButton) {
                downButton.addEventListener("click", function () {
                    updateRows(rows, byName, currentOffset(chordsBlock));
                });
            }
            if (upButton) {
                upButton.addEventListener("click", function () {
                    updateRows(rows, byName, currentOffset(chordsBlock));
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
