# Song browsing & search

## What it does

The homepage shows the entire catalog as a collapsible, letter-grouped
tree: artists as expandable "folders" grouped under A/B/C/... headers,
each artist's songs listed underneath. A filter box narrows the tree
live as you type, without a page reload. Clicking an artist name opens
a dedicated artist page listing just their songs.

A search box in the navbar (present on every page) searches across
artist and title, returning matching songs on a results page.

## How it works

- **Homepage** (`SongBrowseController#index`, `GET /`) loads every song
  grouped by artist (`SongService.loadAllGroupedByArtist()`, sorted via
  `CroatianCollator` -- real Croatian/Serbian alphabetical order, not
  just case-insensitive raw character comparison), then groups those
  artists by first letter in Java (`buildArtistTree`) before handing the
  result to the `index.html` template. This grouping happens in the
  controller, not the template, because Thymeleaf's expression sandbox
  blocks the kind of computation (`T()`/`new`/bean access) this would
  otherwise need inside a loop.
- **Client-side filtering** (`static/js/artist-tree.js`) hides/shows
  artist and song rows against the typed filter text — no server
  round-trip, since the whole tree is already in the DOM.
- **Artist page** (`GET /artists/{artist}`) loads that one artist's songs
  (`SongService.loadByArtist`) and (2026-07-20, SEO) a per-artist meta
  description (`artist.metaDescription` message key, interpolating the
  artist's name and song count) and canonical link, targeting searches
  like "{artist name} akordi" — see `docs/features/sitemap-and-seo.md`.
- **Song page routing**: `GET /akordi/{id}/{slug}` — `id` is the
  authoritative lookup key (the database row id); `slug` is decorative,
  matching pesmarica.rs's own URL convention, and isn't validated
  against the song's real slug. Every successful load also records a
  view (`SongService.recordView`) — a simple aggregate counter per song,
  not tied to any visitor or device.
- **"More by [Artist]" internal linking** (2026-07-22, issue #10 — see
  `~/knowledge/projects/vinoigitare/internal-linking-plan.md`): the song
  page lists up to 8 of the artist's other songs, plus a "See all N
  songs by [Artist]" link to the artist page when there are more than
  that. Reuses `SongService.loadByArtist` (already backing the artist
  page), filtered to exclude the current song. Deliberately just "more
  by the same artist," not a "related/similar songs" list — this app has
  no genre/tag/similarity data to build a genuine "related" signal from
  (genre was intentionally removed from the site entirely, see
  `about-page-and-genre-removal-plan.md`). Placed at the bottom of the
  page, after the chords, not above the title — same reasoning as why
  an earlier visible SEO caption was removed from the top of this page.
- **Search** (`SearchController`, `GET /search?q=...`) delegates to
  `SearchService` (`com.vinoigitare.search`). A blank query renders the
  page with an empty-state message rather than redirecting away, so the
  search box and page stay visible for the visitor to just try again.
- **Alphabetical grouping** for both the homepage tree and (indirectly)
  sorting logic uses `AlphabeticalIndex.firstLetter(...)` — a small
  utility shared wherever "which letter does this artist name belong
  under" needs answering consistently.
- **Real alphabetical order, not code-point order** (`CroatianCollator`,
  2026-07-19): every sort in this app used to compare strings/characters
  by raw Unicode code point (`String.CASE_INSENSITIVE_ORDER`, or plain
  `Character` ordering for the homepage's per-letter headings), which is
  correct for plain A-Z but wrong for `č ć đ š ž` — those code points all
  fall after `z`, so names/letter-headings starting with any of them
  sorted after every plain-Z one instead of where the real
  Croatian/Serbian alphabet places them (`Č`/`Ć` right after `C`, `Đ`
  right after `D`, `Š` right after `S`; only `Ž` genuinely belongs at the
  very end). Fixed with a real `java.text.Collator` for the `hr` locale
  everywhere sorting happens: `SongService`, `SearchService`'s result
  ordering, and `SongBrowseController`'s letter-group headings.
