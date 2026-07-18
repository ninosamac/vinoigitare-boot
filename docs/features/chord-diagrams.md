# Chord diagrams reference

## What it does

A standalone reference page (`/chord-diagrams`, "Chords" in the nav)
showing standard guitar fingering diagrams: every natural root as major
and minor, plus common 7th, sus2/sus4, add9, diminished, and augmented
chords. Not tied to any specific song — a general lookup page, mirroring
pesmarica.rs's own `/dijagramiakorda` page.

The same diagrams are reused as the closing reference section of a
generated personalized songbook PDF (see
[personalized-songbook-and-paywall.md](personalized-songbook-and-paywall.md)).

## How it works

- **`ChordDiagramCatalog`** is the fixed data: which chords exist and
  their fingering positions. Nothing here is per-song or database-backed
  — it's a static reference.
- **`ChordDiagramRenderer`** turns one `ChordDiagram` into a
  self-contained inline SVG string (fretboard grid, finger position
  dots, barre lines, open/muted-string markers).
- **`ChordDiagramController`** (`GET /chord-diagrams`) renders every
  diagram in the catalog up front, in Java, into `RenderedChordDiagram`/
  `RenderedChordSection` records — not inside a Thymeleaf `th:each`
  expression, since calling a Spring bean's method per-iteration inside a
  directly-rendered template hits Thymeleaf's restricted-expression
  guard.
- **Reused by the PDF renderers**: `RenderedChordDiagram`/
  `RenderedChordSection` originally lived nested inside
  `ChordDiagramController` but were promoted to the `com.vinoigitare.chords`
  package once `SongbookPdfRenderer` (a `pdf`-package class) also needed
  them — depending on nested types from a `web`-package controller would
  have been backwards layering.
- **PDF rendering caveat**: openhtmltopdf (the library that turns
  Thymeleaf HTML into a PDF) has no SVG support at all out of the box —
  the `openhtmltopdf-svg-support` dependency (Apache Batik under the
  hood) is what actually lets the chord-diagram SVGs render inside a
  generated PDF instead of showing up blank.
