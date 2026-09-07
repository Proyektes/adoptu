package com.adoptu.frontend

import com.universaliun.auth.web.createPasskeyCredential
import com.universaliun.auth.web.getPasskeyAssertion
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlin.js.Promise

external fun encodeURIComponent(str: String): String

private val webAuthnScope = MainScope()

/**
 * Registration/login orchestration for this app's own `/api/auth/registration-options` +
 * `/api/auth/register` and `/api/auth/assertion-options` + `/api/auth/authenticate` endpoints.
 * The actual WebAuthn browser-API/base64url bridge is AuthKit's own `createPasskeyCredential`/
 * `getPasskeyAssertion` (`com.universaliun.auth.web`) -- this module previously hand-rolled that
 * same logic itself, with a latent bug: it treated the backend's `optionsJson` as a flat
 * `PublicKeyCredentialCreationOptions` object, when the backend (via Yubico's
 * `toCredentialsCreateJson()`, called from AuthKit's own `StartPasskeySignupService`/
 * `StartPasskeyLoginService`) actually wraps it as `{"publicKey": {...}}` -- the exact shape
 * AuthKit's bridge expects and correctly unwraps. Delegating to AuthKit fixes that mismatch
 * instead of just deduplicating the code.
 */
@JsExport
@JsName("webauthn")
object WebAuthnModule {
    fun register(email: String, displayName: String): Promise<dynamic> = webAuthnScope.promise {
        console.log("Starting passkey registration for: $email")
        val start = getRegistrationOptions(email, displayName).await()
        val requestId = start.requestId as String
        val credentialJson = createPasskeyCredential(start.optionsJson as String)
        val language = window.localStorage.getItem("preferredLanguage") ?: "en"
        val jsonStr = """{"requestId":"$requestId","email":"${email.replace("\"", "\\\"")}","displayName":"${displayName.replace("\"", "\\\"")}","language":"$language","credentialJson":${window.asDynamic().JSON.stringify(credentialJson)}}"""
        console.log("Encoded json: " + jsonStr)
        val fetchResult = window.asDynamic().fetch(
            "/api/auth/register",
            js("({method: 'POST', headers: {'Content-Type': 'application/json'}, body: jsonStr})"),
        ).unsafeCast<Promise<dynamic>>().await()
        console.log("Server responded: " + fetchResult.status)
        fetchResult.json().unsafeCast<Promise<dynamic>>().await()
    }

    // For an already-authenticated user adding an additional passkey from Profile settings -
    // distinct from register() above, which is the brand-new-signup flow and calls
    // /api/auth/registration-options (rejects already-registered emails, so it can never work for
    // a logged-in user adding a second passkey). Uses /api/auth/registration-options-for-user +
    // /api/auth/register-passkey instead, which derive the user from the session cookie rather
    // than a submitted email.
    fun registerAdditional(): Promise<dynamic> = webAuthnScope.promise {
        val start = apiFetch("/api/auth/registration-options-for-user", js("({method: 'POST'})")).await()
        val requestId = start.requestId as String
        val credentialJson = createPasskeyCredential(start.optionsJson as String)
        val jsonStr = """{"requestId":"$requestId","credentialJson":${window.asDynamic().JSON.stringify(credentialJson)}}"""
        apiFetch("/api/auth/register-passkey", js("({method: 'POST', body: jsonStr})")).await()
    }

    fun authenticate(): Promise<dynamic> = webAuthnScope.promise {
        val start = getAuthenticationOptions().await()
        val requestId = start.requestId as String
        val credentialJson = getPasskeyAssertion(start.optionsJson as String)
        val jsonStr = """{"requestId":"$requestId","credentialJson":${window.asDynamic().JSON.stringify(credentialJson)}}"""
        val fetchResult = window.asDynamic().fetch(
            "/api/auth/authenticate",
            js("({method: 'POST', headers: {'Content-Type': 'application/json'}, body: jsonStr})"),
        ).unsafeCast<Promise<dynamic>>().await()
        fetchResult.json().unsafeCast<Promise<dynamic>>().await()
    }

    private fun getRegistrationOptions(email: String, displayName: String): Promise<dynamic> {
        val language = window.localStorage.getItem("preferredLanguage") ?: "en"
        val body = "email=${encodeURIComponent(email)}&displayName=${encodeURIComponent(displayName)}&language=${encodeURIComponent(language)}"
        console.log("Requesting registration options with: email=$email, displayName=$displayName, language=$language")
        return window.asDynamic().fetch("/api/auth/registration-options", js("({method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: body})")).then { res ->
            console.log("Registration options response status: " + res.status)
            if (res.ok) {
                res.unsafeCast<dynamic>().json().then<dynamic> { json -> json }
            } else {
                res.json().then { json ->
                    val errorMsg = if (json != null && json.error != undefined) json.error as? String else "Registration failed"
                    console.log("Registration options error: " + errorMsg)
                    val error = js("new Error('')")
                    error.message = errorMsg
                    throw error
                }
            }
        }
    }

    private fun getAuthenticationOptions(): Promise<dynamic> = apiFetch("/api/auth/assertion-options")
}
