# Vino i gitare — Documentation

Vino i gitare (vinoigitare.com) is a free online songbook of guitar
chords and lyrics for songs from the ex-Yugoslav region, built with
Spring Boot. It started as a personal project to digitize a physical
songbook (the 2000 "Vino i gitare" edition) and has grown into a
full-featured, installable web app: search and browse, on-screen chord
transposition, PDF downloads (single-song and personalized multi-song
songbooks with a real paywall), offline support, three languages, and a
lightweight admin panel for managing the song catalog.

This `docs/` folder documents what the app actually does today and how
each piece works, for anyone (including future-you) picking the codebase
back up. It's a snapshot of current behavior, not a running history of
how things got built — for the *why* behind a design decision, the
in-code comments and `~/knowledge/projects/vinoigitare/*.md` plan docs
are the deeper source; these files stay focused on *what exists now*.

## Tech stack

- **Spring Boot 3.5 / Java 21** — plain Spring MVC + Thymeleaf
  server-rendered pages, no SPA framework.
- **H2** (file-based in production, in-memory for tests) via plain
  `JdbcTemplate` — no JPA/Hibernate. Query needs are simple enough that a
  hand-written `RowMapper` per entity is more transparent than an ORM.
- **Spring Security** — a single admin credential gates `/admin/**`;
  every other route is public. No visitor accounts exist anywhere in the
  app, by design.
- **Bootstrap 5**, self-hosted via WebJars (no CDN dependency).
- **openhtmltopdf + PDFBox + Batik** — renders Thymeleaf templates
  straight to PDF for both the single-song download and the
  personalized-songbook feature.
- **Stripe** (Checkout + webhooks) — the payment processor for the
  personalized songbook PDF paywall.
- **Resend** (HTTP API, not SMTP) — outbound email for the visitor
  feedback form.

## Architecture at a glance

- `com.vinoigitare.web` — controllers (one per feature area, mostly).
- `com.vinoigitare.model` — the `Song` domain record.
- `com.vinoigitare.storage` — the H2-backed repository, the `.tab`-file
  importer/reconciler, and the legacy text-file repository.
- `com.vinoigitare.service` — `SongService`, the thin layer controllers
  actually call.
- `com.vinoigitare.chords` — chord detection, transposition, and the
  chord-diagram catalog/renderer.
- `com.vinoigitare.pdf` — the two PDF renderers (single song, songbook).
- `com.vinoigitare.search` — the search implementation.
- `com.vinoigitare.security` — the Spring Security configuration.
- `com.vinoigitare.logging` — request/security/error logging
  infrastructure.
- `com.vinoigitare.tools` — standalone CLI utilities, not part of the
  running web app.

No accounts, no visitor-tracking analytics, no cookies beyond
preference/session-necessary ones (locale, CSRF, theme via
`localStorage`, and the admin login session). See
`features/visitor-feedback.md` and `features/preferences-and-nav.md` for
the closest things to "user state" this app has.

## Features

- [Song browsing & search](features/song-browsing-and-search.md)
- [Song display (transpose, zoom, auto-scroll, live view)](features/song-display.md)
- [Chord diagrams reference](features/chord-diagrams.md)
- [Single-song PDF download](features/song-pdf-download.md)
- [Personalized songbook PDF & paywall](features/personalized-songbook-and-paywall.md)
- [Admin song management](features/admin-song-management.md)
- [Visitor feedback form](features/visitor-feedback.md)
- [Offline support (PWA)](features/offline-support-pwa.md)
- [Internationalization (EN/HR/SR)](features/internationalization.md)
- [Preferences & navigation](features/preferences-and-nav.md)
- [Sitemap & SEO](features/sitemap-and-seo.md)
- [Application logging](features/logging.md)
- [Data storage & catalog sync](features/data-storage-and-sync.md)
