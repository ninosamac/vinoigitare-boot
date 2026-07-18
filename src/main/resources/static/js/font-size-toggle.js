/**
 * Large text toggle (button lives in fragments.html's Preferences
 * dropdown, next to the Theme control) -- accessibility-preferences-plan.md.
 *
 * Deliberately mirrors static/js/theme-toggle.js's exact shape: a
 * data-font-size attribute on <html> (normal by default, "large" only
 * when explicitly chosen), persisted in localStorage, restored
 * synchronously in <head> before this file loads (see fragments.html
 * and every other page with its own <head>) so there's no flash of
 * normal-size text while this file loads -- this file only needs to
 * handle the click itself and keep the button's own label in sync.
 *
 * A separate preference from the theme (light/dark/high-contrast) --
 * either can be on independently of the other.
 */
(function () {
    "use strict";

    var STORAGE_KEY = "vinoigitare.fontSize";

    function metaContent(name, fallback) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute("content") : fallback;
    }

    function isLarge() {
        return document.documentElement.getAttribute("data-font-size") === "large";
    }

    function applyFontSize(large) {
        if (large) {
            document.documentElement.setAttribute("data-font-size", "large");
        } else {
            document.documentElement.removeAttribute("data-font-size");
        }
    }

    function initFontSizeToggle() {
        var button = document.querySelector("[data-font-size-toggle]");
        if (!button) {
            return;
        }

        var enableLabel = metaContent("i18n-font-size-enable-large", "Switch to large text");
        var disableLabel = metaContent("i18n-font-size-disable-large", "Switch to normal text");

        function render() {
            var large = isLarge();
            button.setAttribute("aria-pressed", String(large));
            button.setAttribute("aria-label", large ? disableLabel : enableLabel);
        }

        button.addEventListener("click", function () {
            var next = !isLarge();
            applyFontSize(next);
            localStorage.setItem(STORAGE_KEY, next ? "large" : "normal");
            render();
        });

        render();
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initFontSizeToggle);
        } else {
            initFontSizeToggle();
        }
    }
})();
