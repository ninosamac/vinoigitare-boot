# Song display: transpose, zoom, auto-scroll, live view

## What it does

A song's page shows its chords and lyrics with chords visually
highlighted above the lyric lines they belong to. From there, a visitor
can:

- **Transpose** the song up/down half a step at a time, instantly, with
  no page reload.
- **Zoom** the text larger/smaller.
- **Auto-scroll** the page at an adjustable speed — useful for playing
  hands-free from a music stand.
- Switch to a **full-screen "live" view** (large font, auto-scroll,
  no navbar/search box/chrome at all) for actually performing the song,
  as opposed to reading it at a desk.
- **Print** the page directly, or **download it as a PDF** (see
  [song-pdf-download.md](song-pdf-download.md)).

## How it works

- **Chord highlighting**: `ChordLineHighlighter.render(chords)` wraps
  each detected chord token in a `<span>` for CSS styling, splitting the
  raw text on any line-ending style (`\r\n`, `\r`, or `\n` — admin-entered
  text from a browser `<textarea>` arrives as `\r\n`, imported `.tab`
  files use plain `\n`).
- **Chord detection & transposition**: `ChordTransposer` recognizes a
  chord token (root note, optional accidental, optional quality suffix
  like `m`/`maj7`/`sus4`/`add9`, optional slash-bass) and shifts its root
  (and bass note, for slash chords) by a number of semitones. Notably
  supports **German/ex-YU note naming** (`H` = English B-natural, `B` =
  English B-flat — two different, valid root letters, not the same
  note), since the whole catalog is written in that convention.
- **Client-side transpose** (`static/js/transpose.js`): clicking +/-
  re-renders the `.song-chords` block from a `data-original-chords`
  attribute already embedded in the page — the transposition itself
  happens in the browser via a JS port of the same chord logic, so
  there's no server round-trip per click. The *server-side*
  `ChordTransposer` is only invoked again when generating a PDF (the
  current on-screen transpose value is passed as a `?transpose=` query
  param to the download link).
- **Zoom and auto-scroll** (`static/js/display-controls.js`): both
  preferences persist in `localStorage` between visits; whether
  auto-scroll is *currently running* does not (a visitor returning to a
  song shouldn't have it start scrolling on its own).
- **Live/performance view** (`GET /akordi/{id}/live`, `song-live.html`):
  a deliberately separate, minimal template — no shared navbar fragment,
  no search box — rather than a CSS-only "distraction-free mode" toggle
  on the normal page.
- **View counting**: both the normal song page and the live view record
  a view (`SongService.recordView`); the PDF download and admin routes
  do not, since those aren't a real "someone read this song" event in
  the same sense.
