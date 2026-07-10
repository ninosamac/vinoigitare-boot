/**
 * Dark/light theme toggle (button lives in fragments.html's nav).
 *
 * The actual dark-mode CSS (app.css) responds to Bootstrap's own
 * data-bs-theme attribute on <html> -- reused rather than inventing a
 * separate attribute so Bootstrap's own components repaint too, not just
 * this app's custom rules. Three states in storage terms, but only two
 * the user picks between directly:
 *   - no stored preference: prefers-color-scheme decides (see app.css's
 *     `:root:not([data-bs-theme])` media query block)
 *   - "dark" / "light" stored: explicit override, wins either direction
 *
 * The stored preference is also applied synchronously in <head> (see
 * fragments.html), before this file loads, so there's no flash of the
 * wrong theme -- this file only needs to handle the click itself and
 * keep the button's own label in sync.
 */
(function () {
    "use strict";

    var STORAGE_KEY = "vinoigitare.theme";

    function metaContent(name, fallback) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute("content") : fallback;
    }

    function effectiveTheme() {
        var explicit = document.documentElement.getAttribute("data-bs-theme");
        if (explicit === "light" || explicit === "dark") {
            return explicit;
        }
        var prefersDark = typeof window.matchMedia === "function"
            && window.matchMedia("(prefers-color-scheme: dark)").matches;
        return prefersDark ? "dark" : "light";
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
