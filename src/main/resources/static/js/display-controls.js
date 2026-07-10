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
    var DEFAULT_BASE_FONT_SIZE_REM = 0.95; // matches .song-chords base font-size in app.css
    var FONT_STEP_REM = 0.1;

    // The live/performance view (song-live.html) wants a larger starting
    // size -- it's meant to be read from a music stand, not a desk -- via
    // <body data-base-font-rem="..."> rather than a second copy of this
    // whole file. The font-size *step* itself still comes from the one
    // shared localStorage key either way, since "I like text a bit
    // bigger" is a general reading preference, not a per-page one.
    function baseFontSizeRem() {
        var override = document.body.getAttribute("data-base-font-rem");
        var parsed = override === null ? NaN : parseFloat(override);
        return isNaN(parsed) ? DEFAULT_BASE_FONT_SIZE_REM : parsed;
    }

    var MIN_SCROLL_SPEED = 1;
    var MAX_SCROLL_SPEED = 10;
    var DEFAULT_SCROLL_SPEED = 3;

    // Real bug report: speed 10 (the max) was still barely fast enough to
    // keep up with an average song. A first fix just scaled up a flat
    // px/frame constant, but that has a second bug baked in: the live
    // view's larger default font (and anyone's own font-size +/- choice)
    // means more pixels per line, so the same dial position would scroll
    // fewer *lines* per second at a bigger font -- the dial wouldn't mean
    // the same thing on both pages. Fix (suggested by Nino): define speed
    // as lines-per-second and measure the chords block's own actual
    // line-height at scroll time, so "speed 5" scrolls roughly the same
    // number of lines/second regardless of font size. Chosen so speed 1
    // is a slow, readable pace (~6.7s/line) and speed 10 gives real
    // headroom above a typical singing pace (~0.4s/line).
    var MIN_LINES_PER_SECOND = 0.15;
    var MAX_LINES_PER_SECOND = 2.5;

    function linesPerSecond(speed) {
        var t = (speed - MIN_SCROLL_SPEED) / (MAX_SCROLL_SPEED - MIN_SCROLL_SPEED);
        return MIN_LINES_PER_SECOND + t * (MAX_LINES_PER_SECOND - MIN_LINES_PER_SECOND);
    }

    function chordsLineHeightPx(chordsBlock) {
        var computed = parseFloat(window.getComputedStyle(chordsBlock).lineHeight);
        // A unitless/keyword line-height (e.g. "normal") resolves to NaN
        // here rather than a px value -- app.css always sets an explicit
        // one today, but fall back to a reasonable multiple of the font
        // size rather than silently not scrolling at all if that changes.
        return isNaN(computed) ? parseFloat(window.getComputedStyle(chordsBlock).fontSize) * 1.4 : computed;
    }

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
            var size = baseFontSizeRem() + step * FONT_STEP_REM;
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
        var chordsBlock = document.querySelector(".song-chords");
        if (!toggleButton || !chordsBlock) {
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
        var lastTimestamp = null; // requestAnimationFrame timestamp of the previous step, for a real elapsed-time delta

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

        function step(timestamp) {
            if (!scrolling) {
                return;
            }
            // Real elapsed time since the last frame, not an assumed
            // fixed frame rate -- keeps the lines-per-second target
            // accurate regardless of the display's actual refresh rate
            // (a 60Hz monitor, a 120Hz phone, or a throttled background
            // tab all pass the same real seconds between frames).
            if (lastTimestamp === null) {
                lastTimestamp = timestamp;
            }
            var deltaSeconds = (timestamp - lastTimestamp) / 1000;
            lastTimestamp = timestamp;

            var pixelsPerSecond = linesPerSecond(speed) * chordsLineHeightPx(chordsBlock);
            var pixels = pixelsPerSecond * deltaSeconds + carryOverPixels;
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
            lastTimestamp = null; // discard any stale delta from before a stop
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
