// Real bug found 2026-07-15: this stayed "vinoigitare-v1" through every
// deploy since the PWA shipped, including several rounds of songbook.js
// changes -- cache-first static assets (see isStaticAsset below) are
// never re-fetched once cached under a given name, and activate's cleanup
// only deletes caches whose NAME differs from the current CACHE_NAME, so
// an unchanged name means the old, stale entries just persist forever.
// Symptom: a visitor whose browser had already cached an old songbook.js
// kept running it indefinitely -- new visible form fields rendered fine
// (HTML itself is network-first, not cached), but the stale JS had no
// idea they existed, so nothing it wired ever reached the server.
// Bump this string whenever any cache-first static asset changes, or
// returning visitors silently keep the old version.
//
// Bumped again 2026-07-17: song pages switched from stale-while-revalidate
// to network-first (see the fetch handler below) -- this also clears out
// any song pages a device may have cached under the old strategy, since
// activate's cleanup deletes every cache whose name isn't this one.
//
// Bumped again 2026-07-17: songbook.js changed (Phase B public paywall --
// see personalized-songbook-pdf-plan.md) -- cache-first per this file's
// own standing discipline above.
//
// Bumped again 2026-07-18: songbook.js changed again (page-count pricing,
// §1c) -- same reasoning.
//
// Bumped again 2026-07-18: accessibility-preferences-plan.md -- app.css
// grew the high-contrast/large-text rules, theme-toggle.js was rewritten
// for the 3-way Light/Dark/High-Contrast switch, and font-size-toggle.js
// is a brand-new precached file.
const CACHE_NAME = "vinoigitare-v6";

const PRECACHE_URLS = [
    "/offline",
    "/css/app.css",
    "/webjars/bootstrap/5.3.8/css/bootstrap.min.css",
    "/js/theme-toggle.js",
    "/js/font-size-toggle.js",
    "/js/preferences-menu.js",
    "/js/display-controls.js",
    "/js/live-view.js",
    "/js/delete-confirm.js",
    "/js/artist-tree.js",
    "/js/transpose.js",
    "/icons/favicon.svg",
    "/icons/favicon-32.png",
    "/icons/apple-touch-icon.png",
    "/manifest.json",
    "/images/nav-brand.png",
    "/images/vino-i-gitare-cover.png",
    "/images/wine-and-guitar.jpg",
];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_URLS))
    );
    self.skipWaiting();
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches
            .keys()
            .then((keys) =>
                Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
            )
            .then(() => self.clients.claim())
    );
});

function isStaticAsset(pathname) {
    return (
        pathname.startsWith("/webjars/") ||
        pathname.startsWith("/css/") ||
        pathname.startsWith("/js/") ||
        pathname.startsWith("/icons/") ||
        pathname.startsWith("/images/")
    );
}

// /admin/** (stale CSRF tokens on a cached form) and /search and the PDF
// export (both need a live DB query) are deliberately never cached -- see
// offline-support-plan.md's route table.
function isNetworkOnly(pathname) {
    return pathname.startsWith("/admin") || pathname === "/search" || pathname.endsWith("/pdf");
}

// Real bug found 2026-07-17: song pages render Edit/Delete buttons based
// on the visitor's Spring Security session (sec:authorize in song.html,
// added after this file's original stale-while-revalidate strategy was
// designed -- offline-support-plan.md predates that feature). That
// strategy always hands back whatever's already cached before the
// network response arrives, so an admin who'd ever opened a given song
// page while logged out (the overwhelmingly common case -- normal
// browsing) kept seeing that logged-out render indefinitely after
// logging in, on the same device -- reported as "Edit/Delete buttons
// missing on mobile after logging in," but really a caching bug, not a
// mobile-specific one (it just surfaced there first because that's
// where a browsing history predating the login existed).
//
// Tried gating on a JSESSIONID cookie's presence first -- doesn't work
// at all: browsers deliberately omit the Cookie header from what a
// service worker's fetch event can read via request.headers, even for
// the request it's intercepting (confirmed directly -- always reads as
// empty, regardless of what the browser actually sends over the wire).
// No reliable way to ask a service worker "is this request
// authenticated" without extra plumbing (a non-HttpOnly marker cookie
// read via document.cookie in the page and relayed to the worker, or
// the Cookie Store API, which isn't available outside Chromium).
//
// Simpler fix: song pages now share the same network-first strategy as
// any other navigation (see below) instead of their own
// stale-while-revalidate -- still satisfies "stays available offline"
// (cache is the fallback when the network fetch fails), but always
// reflects the real current session when online, for anyone, without
// needing to know who's asking.
function isSongPage(pathname) {
    return /^\/akordi\/\d+\/[^/]+$/.test(pathname);
}

async function cacheFirst(request) {
    const cached = await caches.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    const cache = await caches.open(CACHE_NAME);
    cache.put(request, response.clone());
    return response;
}

async function networkFirst(request) {
    const cache = await caches.open(CACHE_NAME);
    try {
        const response = await fetch(request);
        cache.put(request, response.clone());
        return response;
    } catch (err) {
        return (await cache.match(request)) || caches.match("/offline");
    }
}

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.method !== "GET") return;

    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    if (isNetworkOnly(url.pathname)) return;

    if (isStaticAsset(url.pathname)) {
        event.respondWith(cacheFirst(request));
    } else if (isSongPage(url.pathname) || request.mode === "navigate") {
        event.respondWith(networkFirst(request));
    }
});
