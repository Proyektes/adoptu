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
 * script-src is nonce-based, with no 'unsafe-inline' at all: a fresh per-request nonce (see
 * [CspNonce]) is generated here and must be stamped onto every actual `<script>` tag by the page
 * templates (Shared.kt's commonScripts, EmailVerificationPage.kt, LocationSearchFilters.kt,
 * UIRoutes.kt) - a script tag without the matching nonce attribute is silently dropped by the
 * browser. Every inline event-handler attribute (onClick=/onChange=, both server-rendered and in
 * JS-generated innerHTML) has been converted to a delegated `data-action`/`data-arg` click
 * listener (CommonModule.initClickActions() in Common.kt), so script-src-attr has nothing left to
 * allow and is intentionally omitted - it falls back to script-src per the CSP3 fallback list,
 * which is nonce-only and therefore blocks any inline handler that might get reintroduced.
 *
 * style-src keeps 'unsafe-inline' unconditionally (not nonce-based): inline style="..." attributes
 * are used throughout the page templates and, unlike onClick handlers, can't execute script.
 * Eliminating this would mean moving every inline style to a CSS class, a separate, larger effort.
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
            "script-src 'self' 'nonce-$nonce'",
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
