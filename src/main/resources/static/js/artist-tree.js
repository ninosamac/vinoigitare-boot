/**
 * Interactive behavior for the homepage artist tree (index.html) --
 * collapse-by-default, per-artist expand/collapse, expand-all/collapse-all,
 * and a live search filter across artist names and song titles.
 *
 * Deliberately layered on top of a fully server-rendered, fully expanded
 * page (see SongBrowseController's class Javadoc and index.html's own
 * comment): without this script, every artist and song is already visible
 * and every link already works, so a visitor with JS disabled (or a search
 * engine crawler) gets a complete, if longer, page rather than an empty
 * shell. This script's only job is to make a large tree comfortable to
 * scan by collapsing it down and adding search -- not to make it usable
 * in the first place.
 *
 * Verified first as a standalone mockup (260 fictional artists, ~1,880
 * fictional songs) before this real version was built -- see the
 * migration knowledge base for that exploration.
 */
(function () {
    "use strict";

    function initArtistTree() {
        var tree = document.getElementById("artistTree");
        if (!tree) {
            return;
        }

        var artistRows = Array.from(tree.querySelectorAll("[data-artist-row]"));
        var letterHeadings = Array.from(tree.querySelectorAll(".letter-heading"));

        function songListFor(row) {
            return row.nextElementSibling;
        }

        function setExpanded(row, expanded) {
            row.setAttribute("aria-expanded", String(expanded));
            songListFor(row).classList.toggle("d-none", !expanded);
        }

        function toggle(row) {
            setExpanded(row, row.getAttribute("aria-expanded") !== "true");
        }

        // Collapse everything on load -- the no-JS default (server-rendered
        // fully expanded) is deliberately the *complete* page, not the
        // *compact* one; this script is what makes it compact.
        artistRows.forEach(function (row) {
            setExpanded(row, false);
        });

        tree.addEventListener("click", function (event) {
            // Let the small "open artist page" link navigate normally --
            // everywhere else on the row (twisty, folder icon, artist
            // name, song count, empty space) toggles expand/collapse. The
            // artist name itself is plain text, not a link, precisely so
            // that clicking it toggles rather than navigating away.
            if (event.target.closest("[data-artist-open-link]")) {
                return;
            }
            var row = event.target.closest("[data-artist-row]");
            if (row) {
                toggle(row);
            }
        });

        tree.addEventListener("keydown", function (event) {
            if (event.key !== "Enter" && event.key !== " ") {
                return;
            }
            var row = event.target.closest("[data-artist-row]");
            // Same exception as the click handler: Enter on the open-link
            // itself should navigate, not toggle -- but a real <a> already
            // does that on its own, so only handle Enter/Space when focus
            // is on the row (the link is a separate, independently
            // focusable element inside it).
            if (!row || event.target !== row) {
                return;
            }
            event.preventDefault();
            toggle(row);
        });

        var expandAllBtn = document.getElementById("expandAllBtn");
        var collapseAllBtn = document.getElementById("collapseAllBtn");
        if (expandAllBtn) {
            expandAllBtn.addEventListener("click", function () {
                artistRows.forEach(function (row) { setExpanded(row, true); });
            });
        }
        if (collapseAllBtn) {
            collapseAllBtn.addEventListener("click", function () {
                artistRows.forEach(function (row) { setExpanded(row, false); });
            });
        }

        /* ---- Live filter --------------------------------------------
           Matches against the artist name or any of their song titles;
           a match auto-expands that artist, highlights the matched
           substring, and hides everything (including letter headings)
           that has no match underneath it. */
        var filterInput = document.getElementById("artistTreeFilter");
        var emptyState = document.getElementById("artistTreeEmptyState");
        if (!filterInput) {
            return;
        }

        function clearMarks(root) {
            root.querySelectorAll("mark").forEach(function (mark) {
                var parent = mark.parentNode;
                parent.replaceChild(document.createTextNode(mark.textContent), mark);
                parent.normalize();
            });
        }

        function markMatch(el, query) {
            var text = el.textContent;
            var idx = text.toLowerCase().indexOf(query);
            if (idx === -1) {
                return;
            }
            var before = text.slice(0, idx);
            var match = text.slice(idx, idx + query.length);
            var after = text.slice(idx + query.length);
            el.textContent = "";
            el.appendChild(document.createTextNode(before));
            var mark = document.createElement("mark");
            mark.textContent = match;
            el.appendChild(mark);
            el.appendChild(document.createTextNode(after));
        }

        filterInput.addEventListener("input", function () {
            var query = filterInput.value.trim().toLowerCase();
            var anyVisible = false;

            artistRows.forEach(function (row) {
                var nameEl = row.querySelector("[data-artist-name]");
                clearMarks(row);
                var list = songListFor(row);
                var artistMatches = query === "" || nameEl.textContent.toLowerCase().indexOf(query) !== -1;

                var songRows = Array.from(list.querySelectorAll(".song-row"));
                var anySongMatches = false;
                songRows.forEach(function (songRow) {
                    var titleEl = songRow.querySelector("[data-song-title]");
                    clearMarks(songRow);
                    var songMatches = query === "" || titleEl.textContent.toLowerCase().indexOf(query) !== -1;
                    songRow.classList.toggle("d-none", query !== "" && !songMatches && !artistMatches);
                    if (query !== "" && songMatches) {
                        markMatch(titleEl, query);
                        anySongMatches = true;
                    }
                });

                var rowVisible = query === "" || artistMatches || anySongMatches;
                row.classList.toggle("d-none", !rowVisible);
                if (rowVisible) {
                    anyVisible = true;
                }

                if (query !== "" && artistMatches) {
                    markMatch(nameEl, query);
                }

                if (query === "") {
                    setExpanded(row, false);
                } else if (rowVisible) {
                    setExpanded(row, true);
                }
            });

            letterHeadings.forEach(function (heading) {
                var next = heading.nextElementSibling;
                var anyUnderHeading = false;
                while (next && !next.classList.contains("letter-heading")) {
                    var candidateRow = next.matches("[data-artist-row]") ? next : next.querySelector("[data-artist-row]");
                    if (candidateRow && !candidateRow.classList.contains("d-none")) {
                        anyUnderHeading = true;
                    }
                    next = next.nextElementSibling;
                }
                heading.classList.toggle("d-none", !anyUnderHeading);
            });

            if (emptyState) {
                emptyState.classList.toggle("d-none", anyVisible || query === "");
            }
        });
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initArtistTree);
        } else {
            initArtistTree();
        }
    }

    if (typeof module !== "undefined" && module.exports) {
        module.exports = { init: initArtistTree };
    }
})();
