package com.adoptu.frontend

import kotlinx.browser.window
import kotlin.js.Promise
import kotlin.js.json

data class FetchResult(val ok: Boolean, val statusText: String, val text: String, val status: Int = 0)

private const val REFRESH_PATH = "/api/auth/refresh"

private fun rawFetch(path: String, opts: dynamic): dynamic =
    window.asDynamic().fetch(path, opts).then { response ->
        try {
            val res = response.unsafeCast<dynamic>()
            res.text().then { text ->
                js("({ok: res.ok, statusText: res.statusText, status: res.status, text: text})")
            }
        } catch (e: dynamic) {
            js("({ok: false, statusText: 'error', status: 0, text: ''})")
        }
    }

/**
 * Silent-refresh-on-401: the AuthKit access-token cookie expires after 15 minutes (see
 * accessTokenExpiryMs in AuthRoutes.kt) — without this, a still-active user gets logged out of
 * whichever page/action they're on mid-session, well before the 7-day session they think they
 * have. On a 401, POSTs /api/auth/refresh (which rotates the refresh-token cookie into a fresh
 * access-token cookie) and retries the original request exactly once. Never retries the refresh
 * call itself, or a 401 there would recurse forever — that 401 is the real "you're logged out"
 * signal (no refresh-token cookie, or it's expired/revoked/reused), left alone.
 */
fun doFetch(path: String, options: dynamic = null): Promise<FetchResult> {
    val opts = options ?: js("({})")
    if (opts.credentials == null) {
        opts.credentials = "include"
    }
    val result: dynamic = rawFetch(path, opts).then { result ->
        if (result.status == 401 && path != REFRESH_PATH) {
            rawFetch(REFRESH_PATH, js("({method: 'POST', credentials: 'include'})")).then { refreshResult ->
                if (refreshResult.ok) rawFetch(path, opts) else result
            }
        } else {
            result
        }
    }
    return result.unsafeCast<Promise<FetchResult>>()
}

fun fetchJson(path: String, options: dynamic = null): Promise<dynamic> {
    return doFetch(path, options).then { result ->
        if (result.text.isNotEmpty()) {
            try {
                JSON.parse<dynamic>(result.text)
            } catch (e: Exception) {
                js("({})")
            }
        } else {
            js("({})")
        }
    }
}