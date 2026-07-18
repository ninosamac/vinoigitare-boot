/**
 * Personalized songbook PDF -- public since Phase B (2026-07-17, see
 * ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md).
 * Loaded unconditionally now; Phase A had this admin-gated, since only
 * the free, admin-only direct-generate flow existed yet.
 *
 * Selection lives entirely in localStorage as a JSON array of
 * {id, transpose} objects -- no accounts, same pattern as every other
 * locally-persisted preference in this app (theme, language, font-size).
 * Two independent entry points share this one file: the "add to my
 * songbook" toggle on song.html, and the /songbook page itself.
 */
(function () {
    "use strict";

    var STORAGE_KEY = "vinoigitare.songbook";
    var SETTINGS_KEY = "vinoigitare.songbook.settings";

    function readSettings() {
        try {
            var raw = localStorage.getItem(SETTINGS_KEY);
            var parsed = raw ? JSON.parse(raw) : {};
            return {
                title: typeof parsed.title === "string" ? parsed.title : "",
                includeChordDiagrams: parsed.includeChordDiagrams !== false
            };
        } catch (e) {
            return { title: "", includeChordDiagrams: true };
        }
    }

    function writeSettings(settings) {
        localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    }

    function initSongbookSettings() {
        var titleInput = document.querySelector("[data-songbook-title]");
        var includeDiagramsInput = document.querySelector("[data-songbook-include-diagrams]");
        var titleField = document.querySelector("[data-songbook-title-field]");
        var includeDiagramsField = document.querySelector("[data-songbook-include-diagrams-field]");
        if (!titleInput || !includeDiagramsInput) {
            return;
        }

        function syncHiddenFields(settings) {
            if (titleField) {
                titleField.value = settings.title;
            }
            if (includeDiagramsField) {
                includeDiagramsField.value = String(settings.includeChordDiagrams);
            }
        }

        var settings = readSettings();
        titleInput.value = settings.title;
        includeDiagramsInput.checked = settings.includeChordDiagrams;
        syncHiddenFields(settings);

        titleInput.addEventListener("input", function () {
            var updated = readSettings();
            updated.title = titleInput.value;
            writeSettings(updated);
            syncHiddenFields(updated);
        });
        includeDiagramsInput.addEventListener("change", function () {
            var updated = readSettings();
            updated.includeChordDiagrams = includeDiagramsInput.checked;
            writeSettings(updated);
            syncHiddenFields(updated);
        });
    }

    function readSongbook() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY);
            var parsed = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    }

    function writeSongbook(entries) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    }

    function indexOfSong(entries, id) {
        for (var i = 0; i < entries.length; i++) {
            if (entries[i].id === id) {
                return i;
            }
        }
        return -1;
    }

    function initSongbookToggle() {
        var toggle = document.querySelector("[data-songbook-toggle]");
        if (!toggle) {
            return;
        }
        var songId = toggle.getAttribute("data-song-id");
        var chordsBlock = document.querySelector("[data-original-chords]");
        var addLabel = toggle.getAttribute("data-label-add");
        var removeLabel = toggle.getAttribute("data-label-remove");

        function currentTranspose() {
            var raw = chordsBlock && chordsBlock.getAttribute("data-current-transpose");
            var parsed = parseInt(raw, 10);
            return isNaN(parsed) ? 0 : parsed;
        }

        function refresh() {
            var inSongbook = indexOfSong(readSongbook(), songId) !== -1;
            toggle.textContent = inSongbook ? removeLabel : addLabel;
            toggle.classList.toggle("active", inSongbook);
        }

        toggle.addEventListener("click", function () {
            var entries = readSongbook();
            var existingIndex = indexOfSong(entries, songId);
            if (existingIndex === -1) {
                entries.push({ id: songId, transpose: currentTranspose() });
            } else {
                entries.splice(existingIndex, 1);
            }
            writeSongbook(entries);
            refresh();
        });

        refresh();
    }

    // Swaps the entry at songId with its neighbor one position toward
    // the front (delta -1) or back (delta +1) -- issue #9: this is the
    // one thing that makes the visitor's own arrangement here the order
    // that actually ends up in the PDF (SongbookPdfRenderer no longer
    // re-sorts alphabetically, it renders entries in submission order).
    function moveEntry(songId, delta) {
        var entries = readSongbook();
        var index = indexOfSong(entries, songId);
        var target = index + delta;
        if (index === -1 || target < 0 || target >= entries.length) {
            return;
        }
        var moved = entries.splice(index, 1)[0];
        entries.splice(target, 0, moved);
        writeSongbook(entries);
        initSongbookPage();
    }

    function renderRow(song, entry, index, total, labels) {
        var row = document.createElement("li");
        row.className = "songbook-row d-flex justify-content-between align-items-center mb-2";

        var label = document.createElement("span");
        var transposeNote = entry.transpose ? " (" + (entry.transpose > 0 ? "+" : "") + entry.transpose + ")" : "";
        label.textContent = song.artist + " – " + song.title + transposeNote;

        var controls = document.createElement("span");
        controls.className = "d-flex gap-1";

        // Disabled rather than omitted at the first/last row -- keeps
        // every row's control cluster the same width/shape, and a
        // disabled button is still a clear "there's nowhere to move
        // this" signal rather than the button just silently vanishing.
        var upButton = document.createElement("button");
        upButton.type = "button";
        upButton.className = "btn btn-outline-secondary btn-sm";
        upButton.textContent = "↑";
        upButton.setAttribute("aria-label", labels.moveUp);
        upButton.disabled = index === 0;
        upButton.addEventListener("click", function () {
            moveEntry(song.id, -1);
        });

        var downButton = document.createElement("button");
        downButton.type = "button";
        downButton.className = "btn btn-outline-secondary btn-sm";
        downButton.textContent = "↓";
        downButton.setAttribute("aria-label", labels.moveDown);
        downButton.disabled = index === total - 1;
        downButton.addEventListener("click", function () {
            moveEntry(song.id, 1);
        });

        var removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.className = "btn btn-outline-secondary btn-sm";
        removeButton.textContent = "×";
        removeButton.addEventListener("click", function () {
            var entries = readSongbook();
            var removeIndex = indexOfSong(entries, song.id);
            if (removeIndex !== -1) {
                entries.splice(removeIndex, 1);
                writeSongbook(entries);
            }
            initSongbookPage();
        });

        controls.appendChild(upButton);
        controls.appendChild(downButton);
        controls.appendChild(removeButton);

        row.appendChild(label);
        row.appendChild(controls);
        return row;
    }

    function initSongbookPage() {
        var list = document.querySelector("[data-songbook-list]");
        if (!list) {
            return;
        }
        var emptyState = document.querySelector("[data-songbook-empty]");
        var countDisplay = document.querySelector("[data-songbook-count]");
        var generateButton = document.querySelector("[data-songbook-generate]");
        var selectionField = document.querySelector("[data-songbook-selection-field]");
        var entries = readSongbook();

        list.innerHTML = "";
        if (countDisplay) {
            countDisplay.textContent = String(entries.length);
        }
        if (generateButton) {
            generateButton.disabled = entries.length === 0;
        }
        // No live price preview here (2026-07-18) -- pricing is by
        // rendered page count now (see SongbookPricing), not song count,
        // and page count can't be known client-side without a full
        // server-side render. See songbook.html's comment on why nothing
        // here tries to guess it.
        if (selectionField) {
            selectionField.value = JSON.stringify(entries);
        }
        if (emptyState) {
            emptyState.hidden = entries.length !== 0;
        }
        if (entries.length === 0) {
            return;
        }

        var ids = entries.map(function (entry) {
            return entry.id;
        });
        var labels = {
            moveUp: list.getAttribute("data-label-move-up"),
            moveDown: list.getAttribute("data-label-move-down")
        };
        fetch("/songbook/details?ids=" + encodeURIComponent(ids.join(",")))
            .then(function (response) {
                return response.json();
            })
            .then(function (songs) {
                var songsById = {};
                songs.forEach(function (song) {
                    songsById[song.id] = song;
                });
                entries.forEach(function (entry, index) {
                    var song = songsById[entry.id];
                    if (song) {
                        list.appendChild(renderRow(song, entry, index, entries.length, labels));
                    }
                });
            });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            initSongbookToggle();
            initSongbookSettings();
            initSongbookPage();
        });
    } else {
        initSongbookToggle();
        initSongbookSettings();
        initSongbookPage();
    }
})();
