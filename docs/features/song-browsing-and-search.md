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
  grouped by artist (`SongService.loadAllGroupedByArtist()`, already
  case-insensitively sorted), then groups those artists by first letter
  in Java (`buildArtistTree`) before handing the result to the
  `index.html` template. This grouping happens in the controller, not
  the template, because Thymeleaf's expression sandbox blocks the kind
  of computation (`T()`/`new`/bean access) this would otherwise need
  inside a loop.
- **Client-side filtering** (`static/js/artist-tree.js`) hides/shows
  artist and song rows against the typed filter text — no server
  round-trip, since the whole tree is already in the DOM.
- **Artist page** (`GET /artists/{artist}`) just loads that one artist's
  songs (`SongService.loadByArtist`).
- **Song page routing**: `GET /akordi/{id}/{slug}` — `id` is the
  authoritative lookup key (the database row id); `slug` is decorative,
  matching pesmarica.rs's own URL convention, and isn't validated
  against the song's real slug. Every successful load also records a
  view (`SongService.recordView`) — a simple aggregate counter per song,
  not tied to any visitor or device.
- **Search** (`SearchController`, `GET /search?q=...`) delegates to
  `SearchService` (`com.vinoigitare.search`). A blank query renders the
  page with an empty-state message rather than redirecting away, so the
  search box and page stay visible for the visitor to just try again.
- **Alphabetical grouping** for both the homepage tree and (indirectly)
  sorting logic uses `AlphabeticalIndex.firstLetter(...)` — a small
  utility shared wherever "which letter does this artist name belong
  under" needs answering consistently.
