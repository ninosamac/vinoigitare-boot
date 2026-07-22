# Sitemap & SEO

## What it does

The site is set up to be genuinely findable by search engines: a real
XML sitemap listing every song, canonical URLs, per-page meta
descriptions, and Open Graph tags — not just relying on internal links
being crawlable.

## How it works

- **`/sitemap.xml`** (`SitemapController`) — generated fresh from the
  database on every request (the catalog is small enough that caching
  this isn't worth the complexity), listing the homepage, the About page
  and the chord-diagrams page (the only pages hosting content not
  otherwise generated from the song repository, so they're listed
  explicitly), every artist's `/artists/{artist}` page (added
  2026-07-20 — see below), and every song's canonical
  `/akordi/{id}/{slug}` URL. Absolute URLs are built from the incoming
  request's own scheme/host/port, not a hardcoded base URL, so this
  works correctly whether it's running on localhost or behind its real
  production hostname. Artist names are raw, unslugified strings with
  spaces and diacritics (e.g. "Đorđe Balašević"), unlike song slugs,
  which are already guaranteed ASCII-safe — each artist URL is built via
  `UriComponentsBuilder`'s `.encode()` rather than naive string
  concatenation, which the song-URL loop can get away with only because
  `Song.slug()` is already safe.
- **`robots.txt`** disallows crawling of anything that isn't real,
  indexable content: `/admin`, `/login` (not public), and (added
  2026-07-19, during an SEO check-up) `/search`, `/songbook`, `/user`,
  `/offline` — dynamic/per-visitor pages, or pages with nothing unique
  to say to a search engine. `/songbook` as a prefix also covers
  checkout, the paid-download status page, and the PDF endpoints
  underneath it, without needing a line each.
- **The live-view page** (`/akordi/{id}/live`) carries a `<link
  rel="canonical">` back to the same song's real `/akordi/{id}/{slug}`
  page (added 2026-07-19) — same chords/lyrics, different (full-screen)
  layout for reading off a music stand, so it's told to search engines
  as the same content rather than a duplicate, without blocking it from
  being crawled or shared on its own.
- **Per-song meta descriptions**: `"{Artist} - {Title}: chords and
  lyrics."` (localized via the `seo.chordsAndLyrics` message key — the
  `hr`/`sr` locales also include "stihovi" alongside "tekst", 2026-07-20,
  targeting the common search synonym "stihovi" [lyrics/verses]
  distinct from "tekst") plus, where available, the song's actual first
  lyric line — skipping any chord-only lines at the top, so the excerpt
  shown in search results is real words, not `"C G Am F"`.
- **Song/artist page `<title>` tags** (`song.pageTitle`/`artist.pageTitle`
  message keys, 2026-07-22, issue #11): a real 28-day Search Console
  export showed 189 of 198 pages getting zero clicks despite real
  impressions at decent positions — traced to the title tags (the actual
  clickable blue link text in results, the single biggest CTR lever)
  mentioning neither "akordi" nor "chords" anywhere, unlike the meta
  description. Now `"{Artist} - {Title}: Akordi i tekst - Vino i
  gitare"` / `"{Artist} - Akordi i tekstovi pjesama - Vino i gitare"` —
  the keyword phrase sits before the `- Vino i gitare` suffix so it
  survives Google's ~50-60 character truncation even if the tail
  doesn't.
- **Per-artist meta descriptions**: `"{Artist} - akordi i tekstovi
  pjesama, {N} pjesama."` (the `artist.metaDescription` message key,
  added 2026-07-20) — targets searches like "{artist name} akordi". The
  song count fills the role the per-song description's excerpted lyric
  line plays: a real, concrete fact about the page, not padding, since
  there's no single lyric line to pull from an artist listing.
- **`forward-headers-strategy: framework`**: in production the app sits
  behind Caddy, which terminates HTTPS and proxies to the app over plain
  HTTP on localhost. Without this setting, Spring Boot ignores the
  `X-Forwarded-Proto` header Caddy sends by default (a deliberate
  security default — blindly trusting it would let a client spoof its
  own scheme if the app were ever exposed directly), so every absolute
  URL the app builds from the incoming request would report `http`, even
  though every real visitor is on `https`. This is what makes the
  sitemap (and everything else building an absolute URL from the
  request) report the correct scheme in production.
- **Canonical links + Open Graph tags** on individual pages (About,
  Chord Diagrams, artist pages, song pages) reduce duplicate-content
  risk and improve how the page looks when shared/linked elsewhere.
  Chord Diagrams got this treatment 2026-07-19, artist pages 2026-07-20
  (both went through the shared `fragments :: head(title)` fragment
  before, which has no room for a page-specific description/canonical —
  found missing despite being genuinely valuable content/a real,
  concrete search target). The artist page's canonical URL is built via
  Thymeleaf's own `@{...}` URL syntax (same as the song page's), so the
  artist name is automatically, correctly URL-encoded.
- **Google Search Console**: domain ownership verified via a DNS TXT
  record (durable — not tied to any page's `<head>`, unlike a meta-tag
  verification would be). Sitemap submission and per-URL "Request
  Indexing" are manual steps inside Search Console itself, outside this
  codebase — see `~/knowledge/projects/vinoigitare/progress.md`'s SEO
  pass entry for why on-page fixes alone don't get a brand-new domain
  with zero backlinks indexed quickly.
