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
 * script-src is nonce-based: a fresh per-request nonce (see [CspNonce]) is generated here and
 * must be stamped onto every actual `<script>` tag by the page templates (Shared.kt's
 * commonScripts, EmailVerificationPage.kt, LocationSearchFilters.kt, UIRoutes.kt) - a script tag
 * without the matching nonce attribute is silently dropped by the browser. 'unsafe-inline' is
 * still listed alongside the nonce as a no-op fallback for pre-CSP2 browsers, which ignore the
 * nonce keyword and fall back to it; CSP2+ browsers ignore 'unsafe-inline' once a nonce-source is
 * present.
 *
 * script-src-attr stays 'unsafe-inline': nonces cannot be applied to inline event-handler
 * attributes (the onClick="..."/onChange="..." used on several pages), and rewriting those into
 * addEventListener-based wiring in common.js is a separate, larger refactor. This is a deliberate,
 * narrower allowance - script-src itself (element and URL loading) is what's nonce-locked, which
 * closes off the more common XSS delivery path (injecting a whole `<script src="...">` tag).
 *
 * style-src keeps 'unsafe-inline' unconditionally (not nonce-based): inline style="..." attributes
 * are used throughout the page templates and, unlike onClick handlers, can't execute script.
 */
class SecurityHeadersFilter : Filter {
    override fun filter(chain: FilterChain, req: RoutingRequest, res: RoutingResponse) {
        val nonce = CspNonce.generate()
        CspNonce.set(nonce)
        try {
            // Must be set before chain.proceed() - handlers commit headers as soon as they write
            // the body (e.g. res.send(...)), so setting these afterward throws
            // IllegalStateException and takes the whole connection down (unlike AccessLogFilter,
            // which only reads res.status() after proceed() and never mutates headers post-send).
            res.header(HeaderNames.create("X-Content-Type-Options"), "nosniff")
            res.header(HeaderNames.create("X-Frame-Options"), "DENY")
            res.header(HeaderNames.create("Referrer-Policy"), "strict-origin-when-cross-origin")
            res.header(HeaderNames.create("Strict-Transport-Security"), "max-age=63072000; includeSubDomains")
            res.header(HeaderNames.create("Content-Security-Policy"), buildCsp(nonce))
            chain.proceed()
        } finally {
            CspNonce.clear()
        }
    }

    companion object {
        private fun buildCsp(nonce: String) = listOf(
            "default-src 'self'",
            "script-src 'self' 'nonce-$nonce' 'unsafe-inline'",
            "script-src-attr 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: https://static.adopt-u.org https://*.amazonaws.com",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'"
        ).joinToString("; ")
    }
}
