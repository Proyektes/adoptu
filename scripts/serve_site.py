#!/usr/bin/env python3
"""Local static file server for the generated site (frontend/build/site), mirroring how
CloudFront will serve it in production: clean URLs (no .html extension), the two dynamic
path-param rewrites (/pet/<id>, /temporal-home/<id>) documented in serve.json (written by
SiteGenerator.kt - the same rewrite table an eventual CloudFront Function should implement, so
local and prod behavior stay in sync), and a /api/* reverse proxy to the real backend (mirroring
the catch-all ordered_cache_behavior CloudFront's single distribution will use to keep the site
and the API same-origin - see infra/cloudfront.tf). Without that proxy, fetch("/api/...") calls
made by the site's own JS would hit this static server instead of the backend and 404, since
they're relative URLs resolved against whatever origin served the page.

Usage: python3 scripts/serve_site.py <site_dir> [port] [backend_url]
The real backend (./gradlew :backend:run, default assumed at http://localhost:8080 - override
with the third arg if ADOPTU_PORT is set to something else) must be running separately.
"""
import http.client
import http.server
import json
import re
import sys
import urllib.parse
from pathlib import Path

DEFAULT_PORT = 4000
DEFAULT_BACKEND_URL = "http://localhost:8080"

# Mirrors infra/cloudfront-functions/site-rewrite.js's CRAWLER_UA_RE - keep both in sync. Lets
# `curl -A "facebookexternalhit/1.1" http://localhost:4000/pet/1` exercise the same bot-routing
# locally that CloudFront does in prod, without needing a deployed distribution to test against.
CRAWLER_UA_RE = re.compile(
    r"facebookexternalhit|Facebot|Twitterbot|WhatsApp|LinkedInBot|Slackbot|TelegramBot|"
    r"Discordbot|SkypeUriPreview|Pinterest|redditbot|Applebot",
    re.IGNORECASE,
)
PET_PATH_RE = re.compile(r"^/pet/([0-9]+)$")


def load_rewrites(site_dir: Path):
    serve_json = site_dir / "serve.json"
    if not serve_json.exists():
        return []
    config = json.loads(serve_json.read_text())
    return [(re.compile(r["source"]), r["destination"]) for r in config.get("rewrites", [])]


def make_handler(site_dir: Path, rewrites, backend_url: str):
    backend = urllib.parse.urlparse(backend_url)

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(site_dir), **kwargs)

        def do_GET(self):
            if self.path.startswith("/api/"):
                return self._proxy("GET")
            if self._rewrite_for_crawler():
                return self._proxy("GET")
            self.path = self._resolve(self.path)
            super().do_GET()

        def do_HEAD(self):
            if self.path.startswith("/api/"):
                return self._proxy("HEAD")
            if self._rewrite_for_crawler():
                return self._proxy("HEAD")
            self.path = self._resolve(self.path)
            super().do_HEAD()

        def _rewrite_for_crawler(self) -> bool:
            """Same job as the CloudFront Function's crawler branch: for a social-preview bot
            hitting /pet/{id}, rewrite to the backend's server-rendered share endpoint instead of
            the static pet-detail.html shell. Returns True if the path was rewritten (caller should
            proxy), False otherwise (caller falls through to normal static resolution)."""
            match = PET_PATH_RE.match(self.path.split("?", 1)[0])
            if not match:
                return False
            ua = self.headers.get("User-Agent", "")
            if not CRAWLER_UA_RE.search(ua):
                return False
            self.path = "/api/share/pet/" + match.group(1)
            return True

        def do_POST(self):
            self._proxy("POST")

        def do_PUT(self):
            self._proxy("PUT")

        def do_PATCH(self):
            self._proxy("PATCH")

        def do_DELETE(self):
            self._proxy("DELETE")

        def do_OPTIONS(self):
            self._proxy("OPTIONS")

        def _proxy(self, method: str):
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length) if length else None
            conn = http.client.HTTPConnection(backend.hostname, backend.port, timeout=30)
            try:
                forward_headers = {
                    k: v for k, v in self.headers.items()
                    if k.lower() not in ("host", "content-length")
                }
                if length:
                    forward_headers["Content-Length"] = str(length)
                conn.request(method, self.path, body=body, headers=forward_headers)
                resp = conn.getresponse()
                self.send_response(resp.status)
                for k, v in resp.getheaders():
                    if k.lower() not in ("transfer-encoding", "connection"):
                        self.send_header(k, v)
                self.end_headers()
                self.wfile.write(resp.read())
            except (ConnectionRefusedError, OSError) as e:
                self.send_response(502)
                self.send_header("Content-Type", "text/plain")
                self.end_headers()
                self.wfile.write(f"backend unreachable at {backend_url}: {e}".encode())
            finally:
                conn.close()

        def _resolve(self, path: str) -> str:
            clean_path = path.split("?", 1)[0].split("#", 1)[0]

            for pattern, destination in rewrites:
                if pattern.match(clean_path):
                    return destination

            if clean_path == "/":
                return "/index.html"

            target = site_dir / clean_path.lstrip("/")
            if target.exists():
                return path

            html_path = site_dir / (clean_path.lstrip("/") + ".html")
            if html_path.exists():
                return clean_path + ".html"

            return path

    return Handler


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    site_dir = Path(sys.argv[1]).resolve()
    port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT
    backend_url = sys.argv[3] if len(sys.argv) > 3 else DEFAULT_BACKEND_URL

    if not site_dir.is_dir():
        print(f"error: {site_dir} does not exist - run ./gradlew :frontend:generateSite first")
        sys.exit(1)

    rewrites = load_rewrites(site_dir)
    handler = make_handler(site_dir, rewrites, backend_url)
    with http.server.ThreadingHTTPServer(("0.0.0.0", port), handler) as httpd:
        print(f"Serving {site_dir} at http://localhost:{port} (proxying /api/* to {backend_url})")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
