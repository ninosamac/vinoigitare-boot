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
const CACHE_NAME = "vinoigitare-v2";

const PRECACHE_URLS = [
    "/offline",
    "/css/app.css",
    "/webjars/bootstrap/5.3.8/css/bootstrap.min.css",
    "/js/theme-toggle.js",
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

async function staleWhileRevalidate(request) {
    const cache = await caches.open(CACHE_NAME);
    const cached = await cache.match(request);
    const network = fetch(request)
        .then((response) => {
            cache.put(request, response.clone());
            return response;
        })
        .catch(() => cached || caches.match("/offline"));
    return cached || network;
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
    } else if (isSongPage(url.pathname)) {
        event.respondWith(staleWhileRevalidate(request));
    } else if (request.mode === "navigate") {
        event.respondWith(networkFirst(request));
    }
});
