# vinoigitare-boot

Spring Boot rewrite of the old Vaadin/OSGi Vinoigitare guitar-chord songbook
("Vino i gitare" — wine and guitars) for ex-Yugoslav songs, on a modern,
mobile-first stack. Live at [vinoigitare.com](https://vinoigitare.com).

## Prerequisites

- **Java 21.** If you don't already have a JDK on `PATH`, install one via
  [SDKMAN](https://sdkman.io/):
  ```
  curl -s "https://get.sdkman.io" | bash
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk install java 21.0.4-tem
  ```
  (`21.0.4-tem` is the exact Temurin build this project was developed and
  verified against — any Java 21 JDK already on `PATH` works too.)
- No separate Maven install needed — this repo ships the Maven Wrapper
  (`./mvnw`), which downloads the right Maven version on first use.

## Build

```
./mvnw verify
```

Compiles the project and runs the full test suite.

## Run

```
./mvnw spring-boot:run
```

Starts the app on port **8080** by default: <http://localhost:8080>.

## Test

```
./mvnw test
```

Runs the whole suite. Tests are tagged `@Tag("fast")` (pure unit / MockMvc
tests, no I/O) or `@Tag("io")` (real file-system and full-Spring-context
tests). There's no dedicated Maven profile or Surefire config wiring these
up yet, but the tags work out of the box with the JUnit Platform provider
via system properties:

```
./mvnw test -Dgroups=fast   # fast tests only
./mvnw test -Dgroups=io     # io tests only
```

## Configuration

Song files live under the directory set by `vinoigitare.songs-dir` in
`src/main/resources/application.yml`, which defaults to `./data/songs` and
can be overridden with the `VINOIGITARE_HOME` environment variable —
mirroring the old app's `VINOIGITARE_HOME/vinoigitare.properties`
convention.

`./data/songs` ships with a few sample `.tab` fixtures (fictional lyrics,
not real song data) so there's something to browse locally out of the box —
one of them is diacritics-heavy (š, đ, č, ć, ž) as a UTF-8 sanity check.

## Tooling: songbook PDF importer

`com.vinoigitare.tools.SongbookPdfImporter` is a standalone CLI (not part
of the running web app) that splits a songbook-style PDF into individual
`.tab` files, reusing the same file-naming/encoding convention as
`TextFileSongRepository`. It was built and tested only against a synthetic
fixture PDF containing fictional made-up songs (see
`SongbookPdfImporterTest`) — point it at material you actually have the
rights to use.

```
./mvnw compile exec:java -Dexec.mainClass=com.vinoigitare.tools.SongbookPdfImporter \
    -Dexec.args="--pdf /path/to/your/songbook.pdf --dry-run"
```

Always start with `--dry-run`: it reports how many songs it detected and
their page ranges/body lengths, without writing anything or printing any
extracted lyric text, so you can sanity-check the split before committing
to it. Once it looks right, drop `--dry-run` and add `--output-dir` — it
deliberately defaults to `./imported` rather than the app's live
`vinoigitare.songs-dir`, so a first attempt can't clobber real data.
`--mode page` (one song per PDF page) and `--artist-first` (swap the
default title-then-artist line order) are available for songbooks laid
out differently than the defaults assume. `--from <page>`/`--to <page>`
(1-indexed, inclusive) restrict processing to a page range — useful for
trying the heuristics against a small slice of a large book (and much
faster to iterate on) before committing to the whole thing; page numbers
in `--dry-run` output are always the real page number in the source PDF,
not relative to `--from`. Run with `--help` for the full option list.

Song-boundary detection is a heuristic, not a real parser — real
songbook layouts vary, and this tool has only ever been validated against
its own synthetic test fixture. If a dry run reports an implausible number
of songs (e.g. far more than the book could plausibly contain), that
usually means either front/back matter (a table of contents, index, or
preface with no internal blank-line gaps) is being swallowed as one giant
"song" per page, or the real layout has blank-line gaps *within* a single
song (between verses, before a repeated chorus, etc.) that are being
mistaken for song boundaries — try `--mode page` instead, or use
`--from`/`--to` to zoom into a small page range and compare block counts
against what you'd expect for that slice. Text extracted from old/scanned
PDFs can also have mis-mapped font encodings that garble diacritics even
though the PDF looks fine on screen: **spot-check a sample of the output
files for mangled š/đ/č/ć/ž before trusting the whole batch.**

## Project status

Live in production at [vinoigitare.com](https://vinoigitare.com). This
file stays focused on building/running the project locally — see
`docs/README.md` and `docs/features/*.md` for what the app actually does
today.
