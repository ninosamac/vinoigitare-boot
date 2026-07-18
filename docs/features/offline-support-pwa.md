# Offline support (PWA)

## What it does

The site can be installed like a real app (desktop, Android, iOS via
Safari's Share menu), and once a song page has been opened while online,
it stays readable offline from then on — no signal needed mid-gig.
Anything never opened before shows a friendly "you're offline" fallback
instead of a browser error. Search, admin, and PDF downloads still need
a live connection.

## How it works

- **`static/manifest.json`** — name, icons, `start_url: "/"`,
  `display: standalone`. This is what makes an install prompt/option
  available at all.
- **`static/sw.js`** (the service worker) — different caching strategy
  per route category:
  - **Static assets** (`/css/**`, `/js/**`, `/webjars/**`, `/icons/**`,
    `/images/**`): cache-first, precached on install.
  - **Everything else navigable** (song pages included): **network-first**,
    falling back to cache, falling back further to `/offline` if neither
    works.
  - **`/admin/**`, `/search`, `/**/pdf`**: network-only, never cached —
    a cached admin page would hand back a stale CSRF token; search needs
    a live query regardless.
- **`CACHE_NAME` must be bumped on every cache-first static-asset
  change** — `activate`'s cleanup only deletes caches whose *name*
  differs from the current one, so leaving the name unchanged after a
  JS/CSS change means returning visitors silently keep running the old
  version forever. This has bitten the app for real more than once (see
  the version-bump history at the top of `sw.js` itself).
- **Song pages were originally stale-while-revalidate, not
  network-first** — changed after a real bug: once the song page began
  rendering admin-only Edit/Delete buttons based on the visitor's login
  session, stale-while-revalidate's "always serve whatever's cached
  first" behavior meant an admin who'd ever viewed a song page while
  logged out kept seeing that logged-out render indefinitely after
  logging in, on the same device. Network-first still satisfies "stays
  available offline" (cache is the fallback when the network fails), but
  always reflects the real current session when online.
- **`/offline`** (`OfflineController`) is the fallback page itself —
  precached on install, so it's always available even if this is the
  very first page a visitor's browser ever tries to show them offline.
