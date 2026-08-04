package com.adoptu.ports

interface CaptchaPort {
    /** Verifies a client-side CAPTCHA challenge token (e.g. Cloudflare Turnstile). */
    suspend fun verify(token: String, remoteIp: String?): Boolean
}
