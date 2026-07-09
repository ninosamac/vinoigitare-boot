/**
 * Font-size and auto-scroll controls for the song page (Phase 4d).
 * Vanilla JS, no framework -- matches the minimal-dependency approach
 * already used for chord transposition (static/js/transpose.js).
 *
 * Font-size *step* and auto-scroll *speed* are persisted in
 * localStorage (they're general reading preferences, not specific to one
 * song), but whether auto-scroll is currently running is NOT persisted --
 * that resets to stopped on every page load, which is the expected
 * behavior (nobody wants the page to suddenly start scrolling itself the
 * next time they open a song).
 */
(function () {
    "use strict";

    var FONT_SIZE_KEY = "vinoigitare.fontSizeStep";
    var SCROLL_SPEED_KEY = "vinoigitare.autoScrollSpeed";

    var MIN_FONT_STEP = -3;
    var MAX_FONT_STEP = 5;
    var BASE_FONT_SIZE_REM = 0.95; // matches .song-chords base font-size in app.css
    var FONT_STEP_REM = 0.1;

    var MIN_SCROLL_SPEED = 1;
    var MAX_SCROLL_SPEED = 10;
    var DEFAULT_SCROLL_SPEED = 3;
    // Pixels per animation frame per speed unit, tuned so speed 3 is a
    // comfortable reading pace at a ~60fps refresh rate.
    var SCROLL_PIXELS_PER_SPEED_UNIT = 0.15;

    function clamp(value, min, max) {
        return Math.max(min, Math.min(max, value));
    }

    function readNumber(key, fallback) {
        var raw = window.localStorage.getItem(key);
        var parsed = raw === null ? NaN : parseInt(raw, 10);
        return isNaN(parsed) ? fallback : parsed;
    }

    function initFontSizeControls() {
        var chordsBlock = document.querySelector(".song-chords");
        var decreaseButton = document.querySelector("[data-font-decrease]");
        var increaseButton = document.querySelector("[data-font-increase]");
        var display = document.querySelector("[data-font-display]");
        if (!chordsBlock || !decreaseButton || !increaseButton) {
            return;
        }

        var step = clamp(readNumber(FONT_SIZE_KEY, 0), MIN_FONT_STEP, MAX_FONT_STEP);

        function render() {
            var size = BASE_FONT_SIZE_REM + step * FONT_STEP_REM;
            chordsBlock.style.fontSize = size + "rem";
            if (display) {
                display.textContent = (step > 0 ? "+" : "") + step;
            }
            window.localStorage.setItem(FONT_SIZE_KEY, String(step));
        }

        decreaseButton.addEventListener("click", function () {
            step = clamp(step - 1, MIN_FONT_STEP, MAX_FONT_STEP);
            render();
        });
        increaseButton.addEventListener("click", function () {
            step = clamp(step + 1, MIN_FONT_STEP, MAX_FONT_STEP);
            render();
        });

        render();
    }

    function metaContent(name, fallback) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute("content") : fallback;
    }

    function initAutoScrollControls() {
        var toggleButton = document.querySelector("[data-scroll-toggle]");
        var slowerButton = document.querySelector("[data-scroll-slower]");
        var fasterButton = document.querySelector("[data-scroll-faster]");
        var speedDisplay = document.querySelector("[data-scroll-speed-display]");
        if (!toggleButton) {
            return;
        }

        // Read from <meta> tags song.html renders via Thymeleaf's message
        // bundle (#{song.autoScrollStart}/#{song.autoScrollStop}) rather
        // than hardcoding English/Serbian text here -- plain JS has no
        // access to Spring's MessageSource.
        var startLabel = metaContent("i18n-auto-scroll-start", "Auto-scroll");
        var stopLabel = metaContent("i18n-auto-scroll-stop", "Pause");

        var speed = clamp(readNumber(SCROLL_SPEED_KEY, DEFAULT_SCROLL_SPEED), MIN_SCROLL_SPEED, MAX_SCROLL_SPEED);
        var scrolling = false;
        var animationFrameId = null;
        var carryOverPixels = 0; // sub-pixel remainder, so low speeds still visibly move over time

        function renderSpeed() {
            if (speedDisplay) {
                speedDisplay.textContent = String(speed);
            }
            window.localStorage.setItem(SCROLL_SPEED_KEY, String(speed));
        }

        function stop() {
            scrolling = false;
            toggleButton.textContent = startLabel;
            toggleButton.setAttribute("aria-pressed", "false");
            if (animationFrameId !== null) {
                window.cancelAnimationFrame(animationFrameId);
                animationFrameId = null;
            }
        }

        function step() {
            if (!scrolling) {
                return;
            }
            var pixels = speed * SCROLL_PIXELS_PER_SPEED_UNIT + carryOverPixels;
            var wholePixels = Math.floor(pixels);
            carryOverPixels = pixels - wholePixels;
            window.scrollBy(0, wholePixels);

            var atBottom = window.innerHeight + window.scrollY >= document.body.scrollHeight - 1;
            if (atBottom) {
                stop();
                return;
            }
            animationFrameId = window.requestAnimationFrame(step);
        }

        function start() {
            if (scrolling) {
                return;
            }
            scrolling = true;
            toggleButton.textContent = stopLabel;
            toggleButton.setAttribute("aria-pressed", "true");
            animationFrameId = window.requestAnimationFrame(step);
        }

        toggleButton.addEventListener("click", function () {
            if (scrolling) {
                stop();
            } else {
                start();
            }
        });
        if (slowerButton) {
            slowerButton.addEventListener("click", function () {
                speed = clamp(speed - 1, MIN_SCROLL_SPEED, MAX_SCROLL_SPEED);
                renderSpeed();
            });
        }
        if (fasterButton) {
            fasterButton.addEventListener("click", function () {
                speed = clamp(speed + 1, MIN_SCROLL_SPEED, MAX_SCROLL_SPEED);
                renderSpeed();
            });
        }

        renderSpeed();
    }

    function init() {
        initFontSizeControls();
        initAutoScrollControls();
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", init);
        } else {
            init();
        }
    }

    if (typeof module !== "undefined" && module.exports) {
        module.exports = { clamp: clamp };
    }
})();
