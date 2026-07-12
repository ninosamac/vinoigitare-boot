/**
 * Preferences dropdown (2026-07-12): open/close/outside-click/escape-key
 * plumbing only, for the small anchored panel that now holds the theme
 * toggle and language switcher (fragments.html's nav) -- deliberately a
 * dropdown, not a modal/full-screen popup, which would be a much more
 * intrusive way to change two small preferences.
 *
 * Doesn't touch the actual controls inside: static/js/theme-toggle.js
 * still owns the dark/light switch (just looks up [data-theme-toggle]
 * wherever that button currently lives), and the language links are
 * still plain "?lang=xx" anchor navigation, not JS.
 */
(function () {
    "use strict";

    function initPreferencesMenu() {
        var wrapper = document.querySelector("[data-preferences]");
        if (!wrapper) {
            return;
        }

        var toggleButton = wrapper.querySelector("[data-preferences-toggle]");
        var panel = wrapper.querySelector("[data-preferences-panel]");

        function isOpen() {
            return !panel.hasAttribute("hidden");
        }

        function open() {
            panel.removeAttribute("hidden");
            toggleButton.setAttribute("aria-expanded", "true");
        }

        function close() {
            panel.setAttribute("hidden", "");
            toggleButton.setAttribute("aria-expanded", "false");
        }

        toggleButton.addEventListener("click", function (event) {
            // Stop this click from also reaching the document-level
            // outside-click listener below, which would otherwise fire on
            // the same click and immediately close what this just opened.
            event.stopPropagation();
            if (isOpen()) {
                close();
            } else {
                open();
            }
        });

        document.addEventListener("click", function (event) {
            if (isOpen() && !wrapper.contains(event.target)) {
                close();
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && isOpen()) {
                close();
                toggleButton.focus();
            }
        });
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initPreferencesMenu);
        } else {
            initPreferencesMenu();
        }
    }
})();
