package com.adoptu.frontend

import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.js.Promise
import kotlin.js.json

external fun encodeURIComponent(str: String): String

@JsExport
@JsName("webauthn")
object WebAuthnModule {
    private val navigator: dynamic
        get() = window.asDynamic().navigator

    // AuthKit's passkey start responses wrap the actual WebAuthn options as a JSON *string*
    // under optionsJson, plus a requestId that must be echoed back on the matching finish call --
    // the server-side ceremony state (the challenge) is looked up by that id, not derived from
    // the caller's identity the way the previous native implementation did it (which only ever
    // needed email/session, no requestId at all). Mirrors Mazmobi's own AuthKit frontend cutover
    // (webauthn.service.ts) -- optionsJson parses to the WebAuthn spec's flat
    // PublicKeyCredentialCreationOptionsJSON/PublicKeyCredentialRequestOptionsJSON shape (no
    // "publicKey" wrapper at this level; parseCreationOptions/parseAssertionOptions below build
    // that wrapper only when calling navigator.credentials.*).
    fun register(email: String, displayName: String): Promise<dynamic> {
        console.log("Starting passkey registration for: $email")
        var requestId = ""
        return getRegistrationOptions(email, displayName)
            .then<dynamic> { start ->
                console.log("Got registration options, creating credential")
                requestId = start.requestId as String
                val options = js("JSON.parse")(start.optionsJson)
                val publicKey = parseCreationOptions(options)
                try {
                    navigator.credentials.create(json("publicKey" to publicKey))
                } catch (e: dynamic) {
                    console.log("Credential creation error: $e")
                    throw e
                }
            }
            .then<dynamic> { credential ->
                if (credential == null) {
                    console.log("Passkey registration was cancelled by user")
                    throw js("new Error('Passkey registration was cancelled. Please try again or use password registration.')")
                }
                console.log("Got credential, encoding: " + credential)
                val idBase64 = arrayBufferToBase64(credential.id)
                val rawIdBase64 = arrayBufferToBase64(credential.rawId)
                val clientDataJSON = credential.response.clientDataJSON
                val clientBytes = js("new Uint8Array(clientDataJSON)")
                var clientBinary = ""
                for (i in 0 until clientBytes.length) {
                    clientBinary += js("String.fromCharCode(clientBytes[i])")
                }
                val clientBase64 = toBase64Url(window.btoa(clientBinary))
                val attestationObject = credential.response.attestationObject
                val attBytes = js("new Uint8Array(attestationObject)")
                var attBinary = ""
                for (i in 0 until attBytes.length) {
                    attBinary += js("String.fromCharCode(attBytes[i])")
                }
                val attBase64 = toBase64Url(window.btoa(attBinary))
                val credentialJsonStr = """{"id":"$idBase64","type":"public-key","rawId":"$rawIdBase64","response":{"clientDataJSON":"$clientBase64","attestationObject":"$attBase64"}}"""
                val jsonStr = """{"requestId":"$requestId","email":"${email.replace("\"", "\\\"")}","displayName":"${displayName.replace("\"", "\\\"")}","credentialJson":${window.asDynamic().JSON.stringify(credentialJsonStr)}}"""
                console.log("Encoded json: " + jsonStr)
                window.asDynamic().fetch("/api/auth/register", js("({method: 'POST', headers: {'Content-Type': 'application/json'}, body: jsonStr})")).then { res ->
                    console.log("Server responded: " + res.status)
                    res.unsafeCast<dynamic>().json().then<dynamic> { json -> json }
                }
            }
    }

    fun authenticate(): Promise<dynamic> {
        var requestId = ""
        return getAuthenticationOptions()
            .then<dynamic> { start ->
                requestId = start.requestId as String
                val options = js("JSON.parse")(start.optionsJson)
                val publicKey = parseAssertionOptions(options)
                navigator.credentials.get(json("publicKey" to publicKey))
            }
            .then<dynamic> { credential ->
                if (credential == null) {
                    console.log("Passkey authentication was cancelled by user")
                    throw js("new Error('Passkey login was cancelled. Please try again or use password login.')")
                }
                val idBase64 = arrayBufferToBase64(credential.id)
                val rawIdBase64 = arrayBufferToBase64(credential.rawId)
                val clientDataBase64 = arrayBufferToBase64(credential.response.clientDataJSON)
                val authenticatorDataBase64 = arrayBufferToBase64(credential.response.authenticatorData)
                val signatureBase64 = arrayBufferToBase64(credential.response.signature)
                val userHandle = credential.response.userHandle
                val userHandleField = if (userHandle != null && userHandle != undefined) {
                    ""","userHandle":"${arrayBufferToBase64(userHandle)}""""
                } else {
                    ""
                }
                val credentialJsonStr = """{"id":"$idBase64","type":"public-key","rawId":"$rawIdBase64","response":{"clientDataJSON":"$clientDataBase64","authenticatorData":"$authenticatorDataBase64","signature":"$signatureBase64"$userHandleField}}"""
                val jsonStr = """{"requestId":"$requestId","credentialJson":${window.asDynamic().JSON.stringify(credentialJsonStr)}}"""
                window.asDynamic().fetch("/api/auth/authenticate", js("({method: 'POST', headers: {'Content-Type': 'application/json'}, body: jsonStr})")).then { res ->
                    res.unsafeCast<dynamic>().json().then<dynamic> { json -> json }
                }
            }
    }

    // options is now the flat WebAuthn spec object AuthKit/Yubico produces (parsed from
    // optionsJson) -- no "publicKey"-wrapper level above it the way the retired native response
    // had, unlike parseCreationOptions/parseAssertionOptions' RETURN value, which the caller
    // still wraps under "publicKey" itself when invoking navigator.credentials.*.
    private fun parseAssertionOptions(options: dynamic): dynamic {
        options.challenge = base64ToArrayBuffer(options.challenge as String)
        val allowCredentials = options.allowCredentials
        if (allowCredentials != null && allowCredentials.length > 0) {
            for (i in 0 until allowCredentials.length) {
                allowCredentials[i].id = base64ToArrayBuffer(allowCredentials[i].id)
            }
        }
        return options
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

    private fun getAuthenticationOptions(): Promise<dynamic> {
        return apiFetch("/api/auth/assertion-options")
    }

    private fun parseCreationOptions(pk: dynamic): dynamic {
        val userEntity = js("({})")
        userEntity.id = base64ToArrayBuffer(pk.user.id)
        userEntity.name = pk.user.name
        userEntity.displayName = pk.user.displayName
        val converted = js("({})")
        converted.rp = pk.rp
        converted.user = userEntity
        converted.challenge = base64ToArrayBuffer(pk.challenge)
        converted.pubKeyCredParams = pk.pubKeyCredParams
        val excludeList = pk.excludeCredentials
        if (excludeList != null) {
            val convertedList = js("[]")
            for (i in 0 until excludeList.length) {
                val exc = excludeList[i]
                val excConverted = js("({})")
                excConverted.id = base64ToArrayBuffer(exc.id)
                excConverted.type = exc.type
                convertedList.push(excConverted)
            }
            converted.excludeCredentials = convertedList
        }
        converted.timeout = pk.timeout
        converted.attestation = pk.attestation
        converted.extensions = pk.extensions
        return converted
    }

    private fun toBase64Url(base64: String): String {
        return base64.replace("+", "-").replace("/", "_").replace("=", "")
    }

    private fun base64ToArrayBuffer(base64: String): dynamic {
        val standardBase64 = base64.replace("-", "+").replace("_", "/")
        val padded = standardBase64 + "==".substring(0, (4 - standardBase64.length % 4) % 4)
        val binary = window.atob(padded)
        val len = binary.length
        val bytes = js("new Uint8Array(len)")
        for (i in 0 until len) {
            js("bytes[i] = binary.charCodeAt(i)")
        }
        return bytes.buffer
    }

    private fun arrayBufferToBase64(buffer: dynamic): String {
        val bytes = js("new Uint8Array(buffer)")
        var binary = ""
        for (i in 0 until bytes.length) {
            binary += js("String.fromCharCode(bytes[i])")
        }
        return toBase64Url(window.btoa(binary))
    }
}