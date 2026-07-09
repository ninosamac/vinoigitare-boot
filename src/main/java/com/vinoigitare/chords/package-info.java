/**
 * Chord-line detection and semitone transposition (Phase 4b of the
 * migration plan): the flagship pesmarica.rs-parity feature. See
 * {@link com.vinoigitare.chords.ChordTransposer} for the algorithm, and
 * {@code src/main/resources/static/js/transpose.js} for the client-side
 * mirror used by the on-screen song page -- the two are independent
 * implementations (Java vs. JavaScript) that must produce the same
 * semitone-shift results, not literally shared code.
 */
package com.vinoigitare.chords;
