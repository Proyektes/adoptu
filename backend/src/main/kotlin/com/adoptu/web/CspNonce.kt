package com.adoptu.web

import java.security.SecureRandom
import java.util.Base64

/**
 * Per-request CSP nonce, set by [SecurityHeadersFilter] before the request reaches its handler
 * and read by page templates (e.g. Shared.kt's commonScripts) to stamp the same value onto each
 * inline <script> tag. A ThreadLocal is safe here because Helidon Nima runs a filter chain and
 * its matched handler synchronously on one virtual thread per request - same assumption
 * AccessLogFilter relies on when it reads res.status() after chain.proceed().
 */
object CspNonce {
    private val secureRandom = SecureRandom()
    private val threadLocal = ThreadLocal<String>()

    fun generate(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun set(value: String) {
        threadLocal.set(value)
    }

    fun current(): String =
        threadLocal.get() ?: error("No CSP nonce set for the current request - was SecurityHeadersFilter skipped?")

    fun clear() {
        threadLocal.remove()
    }
}
