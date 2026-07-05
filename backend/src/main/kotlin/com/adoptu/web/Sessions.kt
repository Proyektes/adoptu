package com.adoptu.web

import com.adoptu.config.AppConfig
import com.adoptu.services.auth.SessionUser
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.ServerRequest
import io.helidon.webserver.http.ServerResponse
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Replaces Ktor's `install(Sessions) { cookie<SessionUser>(...) }` (plugins/Sessions.kt).
 * Cookie value is `base64url(json).base64url(hmacSha256(json))` - an HMAC-authenticated
 * (not encrypted) cookie, same trust model as the Ktor SessionTransportTransformerMessageAuthentication
 * it replaces. Not wire-compatible with old Ktor session cookies - every browser session is
 * invalidated once on cutover, which is expected and harmless (users just log in again).
 */
private const val COOKIE_NAME = "user_session"
private const val MAX_AGE_SECONDS = 86400 * 7

// Must be overridden via ADOPTU_SESSION_SECRET in every real deployment (see application.conf) -
// the checked-in default is dev-only and, unlike a per-deployment secret, forgeable by anyone
// with repo access, which would let them mint a valid session cookie for any user.
private val secretHashKey: ByteArray = run {
    val configured = AppConfig.load().propertyOrNull("session.secretKey")?.getString()
    require(!configured.isNullOrBlank()) { "session.secretKey must be configured (set ADOPTU_SESSION_SECRET)" }
    val bytes = configured.toByteArray()
    require(bytes.size >= 32) { "session.secretKey (ADOPTU_SESSION_SECRET) must be at least 32 bytes" }
    bytes
}
private val hmacKey = SecretKeySpec(secretHashKey, "HmacSHA256")
private val base64UrlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
private val base64UrlDecoder: Base64.Decoder = Base64.getUrlDecoder()

private fun hmac(data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").apply { init(hmacKey) }.doFinal(data)

private fun cookieHeader(req: ServerRequest): String? =
    req.headers().first(HeaderNames.COOKIE).orElse(null)

private fun extractCookie(cookieHeader: String, name: String): String? =
    cookieHeader.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")

fun ServerRequest.getSession(): SessionUser? {
    val raw = cookieHeader(this)?.let { extractCookie(it, COOKIE_NAME) } ?: return null
    val parts = raw.split(".")
    if (parts.size != 2) return null
    val (payloadPart, signaturePart) = parts
    val payload = try { base64UrlDecoder.decode(payloadPart) } catch (e: IllegalArgumentException) { return null }
    val signature = try { base64UrlDecoder.decode(signaturePart) } catch (e: IllegalArgumentException) { return null }
    if (!hmac(payload).contentEquals(signature)) return null
    return try { JsonSupport.objectMapper.readValue(payload, SessionUser::class.java) } catch (e: Exception) { null }
}

fun ServerResponse.setSession(session: SessionUser) {
    val payload = JsonSupport.objectMapper.writeValueAsBytes(session)
    val signature = hmac(payload)
    val value = "${base64UrlEncoder.encodeToString(payload)}.${base64UrlEncoder.encodeToString(signature)}"
    header(
        HeaderNames.SET_COOKIE,
        "$COOKIE_NAME=$value; Path=/; Max-Age=$MAX_AGE_SECONDS; HttpOnly; SameSite=Lax"
    )
}

fun ServerResponse.clearSession() {
    header(HeaderNames.SET_COOKIE, "$COOKIE_NAME=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
}
