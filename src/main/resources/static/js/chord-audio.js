/**
 * Chord playback on /chord-diagrams (issue #12, 2026-07-22): string by
 * string, then all strings together -- real audio synthesis via the
 * browser's native Web Audio API, no audio files and no dependency.
 *
 * Each note is a Karplus-Strong plucked-string: a delay line the length of
 * one period at the target pitch, seeded with noise (the "pluck"), then
 * repeatedly averaged with itself one sample later and fed back with a
 * damping factor -- a simple, well-known algorithm that sounds
 * meaningfully more like a real plucked string than a bare oscillator
 * tone, computed synchronously into a buffer up front (no AudioWorklet/
 * ScriptProcessorNode needed) and played via a plain AudioBufferSourceNode.
 *
 * Fret data (data-frets, a comma-joined string from
 * RenderedChordDiagram#fretsCsv -- see that class's Javadoc for why it
 * arrives pre-formatted rather than as a raw array) combined with standard
 * guitar tuning gives the real pitch of every string: frequency =
 * openStringHz * 2^(fret / 12).
 */
(function () {
    "use strict";

    // Standard tuning, low E to high E -- matches ChordDiagram#frets()'s
    // own index order (index 0 = low E, index 5 = high E).
    var OPEN_STRING_HZ = [82.4069, 110.0000, 146.8324, 195.9977, 246.9417, 329.6276];

    var STRING_BY_STRING_GAP_SECONDS = 0.18;
    var STRING_BY_STRING_NOTE_SECONDS = 1.1;
    var PAUSE_BEFORE_STRUM_SECONDS = 0.35;
    var STRUM_NOTE_SECONDS = 1.8;
    var NOTE_GAIN = 0.25;

    // Created lazily on the first click -- browsers require a user
    // gesture before audio can play at all, and that first click already
    // is one, so there's no earlier safe point to create this anyway.
    // Reused across every button on the page rather than one context per
    // click.
    var audioContext = null;

    function getAudioContext() {
        if (!audioContext) {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            audioContext = new AudioContextClass();
        }
        if (audioContext.state === "suspended") {
            audioContext.resume();
        }
        return audioContext;
    }

    function frequencyFor(stringIndex, fret) {
        return OPEN_STRING_HZ[stringIndex] * Math.pow(2, fret / 12);
    }

    /**
     * Synchronously computes one Karplus-Strong plucked-string buffer at
     * the given frequency and duration. Cheap enough to do per-note,
     * per-click -- a couple of seconds of audio at typical sample rates
     * is at most a few hundred thousand samples, well under what causes
     * any noticeable delay.
     */
    function pluckBuffer(context, frequency, durationSeconds) {
        var sampleRate = context.sampleRate;
        var periodSamples = Math.max(2, Math.round(sampleRate / frequency));
        var ring = new Float32Array(periodSamples);
        for (var i = 0; i < periodSamples; i++) {
            ring[i] = Math.random() * 2 - 1;
        }

        var totalSamples = Math.floor(durationSeconds * sampleRate);
        var buffer = context.createBuffer(1, totalSamples, sampleRate);
        var data = buffer.getChannelData(0);
        var damping = 0.996;
        var index = 0;
        for (var s = 0; s < totalSamples; s++) {
            var current = ring[index];
            data[s] = current;
            var nextIndex = (index + 1) % periodSamples;
            var next = ring[nextIndex];
            ring[index] = damping * 0.5 * (current + next);
            index = nextIndex;
        }
        return buffer;
    }

    /**
     * Schedules one plucked note starting at startTime (an AudioContext
     * timestamp, not wall-clock time -- see playChord). A short linear
     * attack ramp avoids an audible click at note-on; the Karplus-Strong
     * buffer itself already decays to silence, so no separate release
     * ramp is needed at note-off.
     */
    function scheduleNote(context, frequency, startTime, durationSeconds) {
        var buffer = pluckBuffer(context, frequency, durationSeconds);
        var source = context.createBufferSource();
        source.buffer = buffer;

        var gainNode = context.createGain();
        gainNode.gain.setValueAtTime(0, startTime);
        gainNode.gain.linearRampToValueAtTime(NOTE_GAIN, startTime + 0.005);

        source.connect(gainNode).connect(context.destination);
        source.start(startTime);
    }

    /** Non-muted (fret >= 0) string indexes and frets, in string order (low E to high E). */
    function playableNotes(frets) {
        var notes = [];
        for (var s = 0; s < frets.length; s++) {
            if (frets[s] >= 0) {
                notes.push({ stringIndex: s, fret: frets[s] });
            }
        }
        return notes;
    }

    function playChord(frets) {
        var context = getAudioContext();
        var notes = playableNotes(frets);
        if (notes.length === 0) {
            return 0;
        }

        var now = context.currentTime;

        // Phase 1: string by string, low to high, arpeggio-style.
        var lastOnsetOffset = 0;
        notes.forEach(function (note, i) {
            var onsetOffset = i * STRING_BY_STRING_GAP_SECONDS;
            lastOnsetOffset = onsetOffset;
            scheduleNote(context, frequencyFor(note.stringIndex, note.fret), now + onsetOffset,
                    STRING_BY_STRING_NOTE_SECONDS);
        });

        // Phase 2: all strings together (a strum), after the last
        // string-by-string note has had a moment to ring.
        var strumOffset = lastOnsetOffset + PAUSE_BEFORE_STRUM_SECONDS;
        notes.forEach(function (note) {
            scheduleNote(context, frequencyFor(note.stringIndex, note.fret), now + strumOffset, STRUM_NOTE_SECONDS);
        });

        return strumOffset + STRUM_NOTE_SECONDS;
    }

    function parseFrets(csv) {
        return csv.split(",").map(function (value) {
            return parseInt(value, 10);
        });
    }

    function initChordAudio() {
        var buttons = document.querySelectorAll("[data-chord-play]");
        buttons.forEach(function (button) {
            var frets = parseFrets(button.getAttribute("data-frets"));
            button.addEventListener("click", function () {
                if (button.disabled) {
                    return;
                }
                button.disabled = true;
                var totalSeconds = playChord(frets);
                setTimeout(function () {
                    button.disabled = false;
                }, totalSeconds * 1000);
            });
        });
    }

    if (typeof document !== "undefined") {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", initChordAudio);
        } else {
            initChordAudio();
        }
    }
})();
