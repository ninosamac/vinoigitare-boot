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
  (the only static page hosting a download not otherwise generated from
  the song repository, so it's listed explicitly), and every song's
  canonical `/akordi/{id}/{slug}` URL. Absolute URLs are built from the
  incoming request's own scheme/host/port, not a hardcoded base URL, so
  this works correctly whether it's running on localhost or behind its
  real production hostname.
- **Per-song meta descriptions**: `"{Artist} - {Title}: chords and
  lyrics."` (localized via the `seo.chordsAndLyrics` message key) plus,
  where available, the song's actual first lyric line — skipping any
  chord-only lines at the top, so the excerpt shown in search results is
  real words, not `"C G Am F"`.
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
  song pages) reduce duplicate-content risk and improve how the page
  looks when shared/linked elsewhere.
