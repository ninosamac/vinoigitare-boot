# Analytics

## What it does

Two ways to answer "is anyone actually visiting, and what are they
reading" — prompted by Google Search Console showing weak numbers
(Search Console only measures organic-search performance, not overall
traffic, so a separate signal was worth adding):

1. **`/admin/stats`**: an admin-only page showing total songs, total
   views across the whole catalog, and a top-20 most-viewed list.
2. **Cloudflare Web Analytics**: a cookie-less JS beacon on every real
   page, reporting into the Cloudflare dashboard (not this app).

## How it works

- **`AdminController#stats`** (`GET /admin/stats`) sums the `views`
  field already tracked on every `Song` (see
  `SongService#recordView`, wired up since Phase 4e but never
  displayed until now) and sorts a top-20 list descending. Falls under
  the same `/admin/**` authentication gate as every other admin route
  — no new `SecurityConfig` entry needed. No new tracking, no new
  dependency: this is data the app already collects on every song-page
  load.
- **`admin/stats.html`** renders the totals plus a table (artist,
  title linking to the real song page, view count); hidden entirely
  and replaced with a "no views yet" message when total views is 0,
  same empty-state pattern the song page's "More by Artist" section
  uses.
- **Cloudflare Web Analytics** is a `<script>` beacon added to every
  page a browser actually loads (both the shared `fragments :: head`
  fragment and every page that writes its own `<head>` instead —
  `about.html`, `artist.html`, `chord-diagrams.html`, `index.html`,
  `song.html`, `song-live.html` — the same duplication this codebase's
  theme-restore/SW-registration scripts already need). Deliberately
  absent from `song-pdf.html`/`songbook-pdf.html` (PDF-rendering input
  HTML, never loaded in a real browser). No cookies, so no
  consent-banner requirement under GDPR even though the site serves EU
  (Croatian/Serbian) visitors.

## Why this, not something else

Weighed against a 512MB production VPS already running tight (JVM
capped at `-Xmx256m`) and an EU-serving audience: self-hosted
Umami/Plausible-CE/Matomo would need a second database competing for
that same memory; Google Analytics needs a cookie-consent banner;
hosted Plausible/Fathom/Simple Analytics work but cost ~$9-19/month for
a dashboard Cloudflare's existing free tier already covers well enough
to start. Both chosen options are $0 and use infrastructure already in
place (the DB's existing view counter, the domain's existing
Cloudflare account).

**Not built in this pass**: log-based analytics (parsing
`RequestLoggingFilter`'s existing request log for first-party
referrer/device stats) is scoped but deliberately deferred — see
`~/knowledge/projects/vinoigitare/analytics-plan.md`'s Part 3 for the
full design if it's ever picked up.

## Known limitations

- View counts exist only for song pages — `/`, `/artists/*`, and
  `/search` have no counter today.
- No views-over-time breakdown, only a running total (would need a
  timestamped events table, not just a counter).
- Cloudflare Web Analytics setup requires the Cloudflare dashboard
  (manual/JS-snippet method, since the domain's DNS is not proxied
  through Cloudflare) — not something this app's own code or deploy
  process can automate.
