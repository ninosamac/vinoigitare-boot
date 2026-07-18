# Data storage & catalog sync

## What it does

Songs are stored two ways at once, kept in sync automatically: a `.tab`
text file per song (the actual source-of-truth collection, one file
named `"Artist - Title.tab"`, versioned in its own separate git repo,
`vinoigitare-songs`) and a row in an H2 database (what the running app
actually queries against for speed). Editing a song through the admin
panel, or a `.tab` file changing on disk from any other source, ends up
reflected in both places without manual intervention.

## How it works

- **`DatabaseSongRepository`** — the actively-used `SongRepository`
  implementation. Plain `JdbcTemplate` + a hand-written `RowMapper`, not
  JPA/Hibernate: this app's query needs are simple CRUD plus a handful
  of filters/sorts, so an ORM's entity-lifecycle complexity isn't worth
  taking on. Schema lives in `schema.sql`, run automatically at startup.
- **`TabFileMirror`** — the *forward* direction: when the admin panel
  creates/edits/deletes a song, this writes/renames/removes the matching
  `.tab` file on disk, so the database is never the only place a change
  exists.
- **`SongImporter`** — the *reverse* direction, and the trickier one:
  reconciles the database against whatever `.tab` files currently exist
  on disk, matched by `artist + title` (the filename itself). Runs once
  at startup and then periodically for as long as the app keeps running
  — not just once at boot — because a song added via the admin panel on
  one running instance doesn't automatically appear on a *different*
  already-running instance just because its own sync script pulled the
  new file; something has to notice the new file exists and tell that
  instance's own database about it.
  - A new `.tab` file with no matching row gets inserted.
  - A `.tab` file whose content now differs from its matching row
    updates that row's chords **in place** — id, slug, creation
    timestamp, and view count all survive the edit, so the song's URL
    and popularity count don't reset just because the text changed.
  - A database row with no matching `.tab` file gets deleted — **unless**
    the file count looks suspiciously small next to the database (a
    circuit breaker against a failed `git pull` or a momentarily
    unmounted directory being misread as "everything was deleted").
  - **Known, accepted limitation**: a rename isn't recognized as "the
    same song, renamed" on any instance other than the one it happened
    on — it reads as an unrelated delete-and-add elsewhere, resetting
    that song's id/slug/view-count everywhere else. Fixing this would
    need a stable id that travels with the file's content rather than
    its filename; not worth the complexity for how rarely a real rename
    happens.
- **Tests never touch either the real database or the real `.tab`
  files** — every `@SpringBootTest` points at an isolated in-memory H2
  instance and a fresh temp directory instead (see
  `AbstractSpringBootTest`), so running the test suite can never corrupt
  real data or the real catalog.
- **`TextFileSongRepository`** still exists as a second, inactive
  `SongRepository` implementation — a holdover from before the database
  existed, kept behind the same interface (not deleted) per the
  migration plan, and still used directly by `SongImporter` to read the
  `.tab` fixtures. It no longer backs `SongService` directly.
