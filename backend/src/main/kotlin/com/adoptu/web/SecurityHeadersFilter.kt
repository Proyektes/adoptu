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
 * script-src/style-src allow 'unsafe-inline' because Shared.kt (and a few page templates) bootstrap
 * per-page state via inline <script> blocks and elements use inline style="..." - tightening this
 * to nonces/hashes is a larger, separate refactor. Even with 'unsafe-inline', this still blocks
 * loading script/style/img/font/connect from any origin other than the explicit allowlist below,
 * which is the more common XSS delivery path (injecting a `<script src="https://evil.example">`).
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
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
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
