/**
 * Fullscreen toggle for the live/performance view (song-live.html).
 * Vanilla JS, same minimal-dependency approach as the other song-page
 * scripts (transpose.js, display-controls.js, theme-toggle.js).
 *
 * Fullscreen can only be requested from inside a user-gesture event
 * handler -- browsers silently reject a call made on page load -- so
 * this is a plain button click, not something triggered automatically
 * when the live view opens.
 */
(function () {
    "use strict";

    function metaContent(name, fallback) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute("content") : fallback;
    }

    function init() {
        var button = document.querySelector("[data-fullscreen-toggle]");
        if (!button) {
            return;
        }
        if (!document.documentElement.requestFullscreen) {
            // Fullscreen API unsupported (e.g. some mobile browsers) --
            // remove the control rather than show something that can't work.
            button.remove();
            return;
        }

        var enterLabel = metaContent("i18n-fullscreen-enter", "Fullscreen");
        var exitLabel = metaContent("i18n-fullscreen-exit", "Exit fullscreen");

        function render() {
            button.textContent = document.fullscreenElement ? exitLabel : enterLabel;
        }

        button.addEventListener("click", function () {
            if (document.fullscreenElement) {
                document.exitFullscreen();
            } else {
                document.documentElement.requestFullscreen();
            }
        });
        document.addEventListener("fullscreenchange", render);

        render();
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", init);
        } else {
            init();
        }
    }
})();
