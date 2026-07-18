# Admin song management

## What it does

A single admin account can log in and add, edit, or delete songs
through plain HTML forms — no separate CMS, no rich text editor, just
artist/title/chords-and-lyrics fields. Edit and Delete buttons also
appear directly on a song's own page (not just a separate admin list)
when logged in.

## How it works

- **`AdminController`** (`/admin`, `/admin/new`, `/admin/edit/{id}`,
  `/admin/delete/{id}`) is plain CRUD against `SongService`. Editing
  preserves the row's id/creation timestamp/view count; the slug is
  always recomputed from the (possibly changed) artist/title rather than
  kept stale.
- **Authentication**: one credential, configured via
  `VINOIGITARE_ADMIN_USER`/`VINOIGITARE_ADMIN_PASSWORD` (env vars,
  `application.yml`), enforced by Spring Security
  (`com.vinoigitare.security.SecurityConfig`). There is no
  visitor-accounts system anywhere in this app — this is the one
  deliberately-scoped-in piece of that idea, not a reversal of skipping
  it entirely.
- **Allowlist, not a denylist**: `SecurityConfig` lists every *public*
  route explicitly (`permitAll()`); anything not listed falls under
  `.anyRequest().authenticated()` automatically. `/admin/**` and
  `/songbook/generate` (the admin's free direct-PDF-generate endpoint,
  see [personalized-songbook-and-paywall.md](personalized-songbook-and-paywall.md))
  both rely on simply *not* being in that list, rather than an explicit
  deny rule.
- **Audit logging**: every create/update/delete is logged at INFO (see
  [logging.md](logging.md)) — there was no logging on these actions at
  all until the application-logging feature added it.
- **Storage layer**: admin edits go through `DatabaseSongRepository`,
  which also mirrors the change out to a `.tab` file on disk
  (`TabFileMirror`) — see
  [data-storage-and-sync.md](data-storage-and-sync.md) for how that
  reconciles across multiple running instances.
