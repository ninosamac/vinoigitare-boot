/**
 * Dark/light theme toggle (button lives in fragments.html's nav).
 *
 * The actual dark-mode CSS (app.css) responds to Bootstrap's own
 * data-bs-theme attribute on <html> -- reused rather than inventing a
 * separate attribute so Bootstrap's own components repaint too, not just
 * this app's custom rules.
 *
 * Light by default; dark only when explicitly chosen via this button,
 * persisted in localStorage. Deliberately does NOT also follow
 * prefers-color-scheme -- an earlier version did, and it caused a real
 * bug: song.html has its own <head> (doesn't go through fragments ::
 * head, for SEO reasons) and was missing the theme-restore script the
 * other pages have, so navigating to a song page silently fell back to
 * the OS preference instead of the visitor's actual stored choice,
 * making the theme appear to randomly flip mid-navigation. One clear
 * rule -- explicit choice or light, nothing else -- avoids that whole
 * class of bug.
 *
 * The stored preference is also applied synchronously in <head> (every
 * page needs its own copy of that inline script now, see fragments.html
 * and song.html), before this file loads, so there's no flash of dark
 * while this file loads -- this file only needs to handle the click
 * itself and keep the button's own label in sync.
 */
(function () {
    "use strict";

    var STORAGE_KEY = "vinoigitare.theme";

    function metaContent(name, fallback) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute("content") : fallback;
    }

    function effectiveTheme() {
        return document.documentElement.getAttribute("data-bs-theme") === "dark" ? "dark" : "light";
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-bs-theme", theme);
    }

    function initThemeToggle() {
        var button = document.querySelector("[data-theme-toggle]");
        if (!button) {
            return;
        }

        var switchToDarkLabel = metaContent("i18n-theme-switch-to-dark", "Switch to dark mode");
        var switchToLightLabel = metaContent("i18n-theme-switch-to-light", "Switch to light mode");

        function render() {
            var isDark = effectiveTheme() === "dark";
            // The glyph shows what clicking switches TO, not the current
            // state -- a sun to go light, a crescent moon to go dark.
            button.textContent = isDark ? "☀" : "☽";
            button.setAttribute("aria-label", isDark ? switchToLightLabel : switchToDarkLabel);
        }

        button.addEventListener("click", function () {
            var next = effectiveTheme() === "dark" ? "light" : "dark";
            applyTheme(next);
            localStorage.setItem(STORAGE_KEY, next);
            render();
        });

        render();
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initThemeToggle);
        } else {
            initThemeToggle();
        }
    }
})();
