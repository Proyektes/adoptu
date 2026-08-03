package com.adoptu.web

import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Filter
import io.helidon.webserver.http.FilterChain
import io.helidon.webserver.http.RoutingRequest
import io.helidon.webserver.http.RoutingResponse

/**
 * Adds baseline defense-in-depth headers to every response. CloudFront always terminates TLS
 * and redirects http->https to viewers (see infra/cloudfront.tf), so HSTS is safe to send
 * unconditionally - browsers ignore it on a plain-http response anyway.
 *
 * The backend is JSON-API-only now (page rendering moved to the static site - see
 * frontend/src/jvmMain/kotlin/com/adoptu/site/SiteGenerator.kt) and its one remaining HTML
 * response (AuthRoutes.kt's magic-link-login same-origin cookie bounce) has no `<script>` tag at
 * all, so script-src is a flat 'none' - no per-request CSP nonce machinery needed here anymore
 * (that lived in CspNonce.kt/the page templates, both removed with the static-site migration).
 * The equivalent CSP for the static site itself is a CloudFront response headers policy, since
 * that's what actually serves the HTML now.
 *
 * style-src has no 'unsafe-inline' either: every inline style="..." attribute across the page
 * templates has been moved to a CSS class in style.scss (.hidden, .checkbox-row, .mt-2rem, etc).
 * JS-side `element.style.property = value` assignments are untouched and still work fine -
 * CSP only restricts the "style" content attribute (inline markup / setAttribute), not the
 * CSSOM API, and an inline-set property always wins over a class rule in the cascade regardless
 * of source order, so toggling visibility via `.style.display = "block"/"none"` still overrides
 * classes like .hidden correctly.
 */
class SecurityHeadersFilter : Filter {
    override fun filter(chain: FilterChain, req: RoutingRequest, res: RoutingResponse) {
        // Must be set before chain.proceed() - handlers commit headers as soon as they write the
        // body (e.g. res.send(...)), so setting these afterward throws IllegalStateException and
        // takes the whole connection down (unlike AccessLogFilter, which only reads res.status()
        // after proceed() and never mutates headers post-send).
        res.header(HeaderNames.create("X-Content-Type-Options"), "nosniff")
        res.header(HeaderNames.create("X-Frame-Options"), "DENY")
        res.header(HeaderNames.create("Referrer-Policy"), "strict-origin-when-cross-origin")
        res.header(HeaderNames.create("Strict-Transport-Security"), "max-age=63072000; includeSubDomains")
        res.header(HeaderNames.create("Content-Security-Policy"), CSP)
        chain.proceed()
    }

    companion object {
        private val CSP = listOf(
            "default-src 'none'",
            "script-src 'none'",
            "style-src 'self'",
            // blob: is required for client-side photo compression (ImageCompression.kt): the
            // selected file is loaded into an <img> via a blob: object URL before being drawn to
            // canvas and re-encoded - without it the browser blocks that load and the compressor
            // silently falls back to uploading the original, uncompressed file.
            // https://dynamic.adopt-u.org is the pet-photo CDN domain (S3ImageStorageAdapter's
            // publicUrl, see infra/ecs.tf's ADOPTU_S3_PUBLIC_URL) - it's a custom CNAME to
            // CloudFront, not an *.amazonaws.com host, so the wildcard below doesn't cover it and
            // it needs its own entry or every pet photo is blocked by CSP after upload. Kept here
            // (not just on the static site's own CSP) since API responses (e.g. pet JSON) are
            // still what the browser evaluates this policy against on this origin.
            "img-src 'self' data: blob: https://static.adopt-u.org https://dynamic.adopt-u.org https://*.amazonaws.com",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'none'",
            "frame-ancestors 'none'"
        ).joinToString("; ")
    }
}
