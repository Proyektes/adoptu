// Intentionally does no caching - the site is session/API-driven (auth, live pet/report data),
// and caching HTML or API responses risks serving stale or logged-out-looking content. This
// exists only to satisfy the browser's installability requirement (Chrome/Edge require an active
// service worker with a fetch handler before showing an install prompt); every request just goes
// straight to the network exactly as it would with no service worker at all.
self.addEventListener("fetch", () => {});
