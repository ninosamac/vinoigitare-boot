/**
 * Personalized songbook PDF (Phase A, admin-gated -- see
 * ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md).
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

    function renderRow(song, entry) {
        var row = document.createElement("li");
        row.className = "songbook-row d-flex justify-content-between align-items-center mb-2";

        var label = document.createElement("span");
        var transposeNote = entry.transpose ? " (" + (entry.transpose > 0 ? "+" : "") + entry.transpose + ")" : "";
        label.textContent = song.artist + " – " + song.title + transposeNote;

        var removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.className = "btn btn-outline-secondary btn-sm";
        removeButton.textContent = "×";
        removeButton.addEventListener("click", function () {
            var entries = readSongbook();
            var index = indexOfSong(entries, song.id);
            if (index !== -1) {
                entries.splice(index, 1);
                writeSongbook(entries);
            }
            initSongbookPage();
        });

        row.appendChild(label);
        row.appendChild(removeButton);
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
        fetch("/songbook/details?ids=" + encodeURIComponent(ids.join(",")))
            .then(function (response) {
                return response.json();
            })
            .then(function (songs) {
                var songsById = {};
                songs.forEach(function (song) {
                    songsById[song.id] = song;
                });
                entries.forEach(function (entry) {
                    var song = songsById[entry.id];
                    if (song) {
                        list.appendChild(renderRow(song, entry));
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
