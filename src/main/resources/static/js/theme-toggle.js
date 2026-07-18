/**
 * Theme switcher (buttons live in fragments.html's nav, inside the
 * Preferences dropdown).
 *
 * The actual theme CSS (app.css) responds to Bootstrap's own
 * data-bs-theme attribute on <html> -- reused rather than inventing a
 * separate attribute so Bootstrap's own components repaint too, not
 * just this app's custom rules.
 *
 * Light by default; dark or high-contrast only when explicitly chosen,
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
 * and song.html), before this file loads, so there's no flash of the
 * wrong theme while this file loads -- this file only needs to handle
 * clicks and keep the buttons' own active state in sync.
 *
 * Three visible options (Light/Dark/High Contrast), not a single
 * cycling icon button -- accessibility-preferences-plan.md (2026-07-18)
 * decided this in favor of matching the Language switcher's existing
 * pattern right below it in the same dropdown (EN/HR/SR, all shown at
 * once), reasoning that a third option is more discoverable as a
 * visible choice than hidden behind repeated clicks of one icon. High
 * Contrast is a standalone third theme, not a toggle combined with
 * light/dark -- picking it means giving up whichever of light/dark you
 * were on, a deliberately simpler design than a combinable version.
 */
(function () {
    "use strict";

    var STORAGE_KEY = "vinoigitare.theme";
    var VALID_THEMES = ["light", "dark", "high-contrast"];

    function currentTheme() {
        var stored = document.documentElement.getAttribute("data-bs-theme");
        return VALID_THEMES.indexOf(stored) !== -1 ? stored : "light";
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-bs-theme", theme);
    }

    function initThemeSwitch() {
        var buttons = document.querySelectorAll("[data-theme-option]");
        if (buttons.length === 0) {
            return;
        }

        function render() {
            var active = currentTheme();
            buttons.forEach(function (button) {
                var isActive = button.getAttribute("data-theme-option") === active;
                button.classList.toggle("active", isActive);
                button.setAttribute("aria-pressed", String(isActive));
            });
        }

        buttons.forEach(function (button) {
            button.addEventListener("click", function () {
                var theme = button.getAttribute("data-theme-option");
                applyTheme(theme);
                localStorage.setItem(STORAGE_KEY, theme);
                render();
            });
        });

        render();
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initThemeSwitch);
        } else {
            initThemeSwitch();
        }
    }
})();
