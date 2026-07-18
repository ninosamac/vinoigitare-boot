# Single-song PDF download

## What it does

Every song page has a "Download PDF" link that generates a one-page(ish)
PDF of just that song — chords and lyrics, at whatever transposition is
currently showing on screen. Free, instant, no login, no payment.

## How it works

- **Route**: `GET /akordi/{id}/pdf?transpose=N` (`SongPdfController`).
  The `transpose` query param is appended by the page's own JS from
  whatever the visitor currently has the song transposed to, so the PDF
  always matches what's on screen — the actual transposition math runs
  again server-side (`ChordTransposer`), not just visually.
- **Rendering** (`SongPdfRenderer`): renders the `song-pdf.html`
  Thymeleaf template (a real page, styled for print, not a wrapped
  screenshot of the HTML view) through openhtmltopdf. Fonts (DejaVu Sans
  Mono, regular + bold, for the extended Latin characters ex-YU song
  titles/lyrics need — š/đ/č/ć/ž) are loaded from classpath
  `InputStream`s rather than filesystem paths, specifically so this
  works from inside the packaged, deployed jar, not just a local
  checkout.
- **Filenames**: `PdfDownloadFilenames.contentDispositionFor(...)` builds
  both an ASCII-transliterated `filename=` (for older clients) and the
  accurate percent-encoded `filename*=UTF-8''...` form (RFC 6266) in the
  same header, since a raw non-ASCII filename doesn't survive every HTTP
  client correctly on its own.
- **Shared with the songbook feature**: this filename-building helper,
  and the underlying `fragments :: songContent` template fragment for
  the actual title/artist/chords block, are both reused as-is by the
  multi-song [personalized songbook PDF](personalized-songbook-and-paywall.md)
  — not duplicated.
- **Footer credit**: the same small, unobtrusive `vinoigitare.com`
  mention next to the page number as the songbook PDF's footer — added
  here too even though this download is free, so every PDF that leaves
  the site (paid or not) carries it.
