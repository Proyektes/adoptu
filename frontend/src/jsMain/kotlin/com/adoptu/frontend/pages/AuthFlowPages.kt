package com.adoptu.frontend.pages

import com.adoptu.frontend.I18n
import com.adoptu.frontend.RsaCryptoModule
import com.adoptu.frontend.apiFetch
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.js.json

@JsExport
@JsName("ForgotPasswordPage")
object ForgotPasswordPageModule {
    fun init() {
        document.getElementById("submit-btn")?.addEventListener("click", { submit() })
    }

    private fun submit() {
        val msg = document.getElementById("message")
        val emailInput = document.getElementById("email") as? HTMLInputElement
        val email = emailInput?.value ?: ""
        if (email.isEmpty()) {
            msg?.className = "message error"
            msg?.textContent = "Email is required"
            return
        }
        msg?.textContent = "Sending..."
        msg?.className = ""

        RsaCryptoModule.getPublicKey()
            .then { publicKey -> RsaCryptoModule.encrypt(email, publicKey) }
            .then { encrypted ->
                apiFetch("/api/auth/forgot-password", json("method" to "POST", "body" to JSON.stringify(json("encryptedData" to encrypted))))
            }
            .then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = "Password reset link sent! Check your email."
                    emailInput?.value = ""
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.error?.toString() ?: "Failed to send reset link."
                }
            }
            .catch { _: dynamic ->
                msg?.className = "message error"
                msg?.textContent = "Failed to send reset link."
            }
    }
}

@JsExport
@JsName("ResetPasswordPage")
object ResetPasswordPageModule {
    fun init() {
        val token = tokenFromUrl()
        if (token == null) {
            document.getElementById("message")?.let {
                it.className = "message error"
                it.textContent = "Invalid or missing token."
            }
            (document.getElementById("submit-btn") as? HTMLButtonElement)?.disabled = true
            return
        }
        document.getElementById("submit-btn")?.addEventListener("click", { submit(token) })
    }

    private fun tokenFromUrl(): String? {
        val params = js("new URLSearchParams(window.location.search)")
        return params.get("token") as? String
    }

    private fun submit(token: String) {
        val msg = document.getElementById("message")
        val password = (document.getElementById("password") as? HTMLInputElement)?.value ?: ""
        val confirmPassword = (document.getElementById("confirm-password") as? HTMLInputElement)?.value ?: ""

        fun fail(text: String) {
            msg?.className = "message error"
            msg?.textContent = text
        }

        if (password.length < 8) { fail(I18n.t("passwordTooShort").ifEmpty { "Password must be at least 8 characters." }); return }
        if (!Regex("[A-Z]").containsMatchIn(password)) { fail(I18n.t("passwordNeedUppercase").ifEmpty { "Password needs at least 1 uppercase letter." }); return }
        if (!Regex("[a-z]").containsMatchIn(password)) { fail(I18n.t("passwordNeedLowercase").ifEmpty { "Password needs at least 1 lowercase letter." }); return }
        if (!Regex("[0-9]").containsMatchIn(password)) { fail(I18n.t("passwordNeedNumber").ifEmpty { "Password needs at least 1 number." }); return }
        if (!Regex("[!@#\$%^&*(),.?\":{}|<>\\-_+=/\\[\\]\\\\|°º«»¿]").containsMatchIn(password)) { fail(I18n.t("passwordNeedSymbol").ifEmpty { "Password needs at least 1 symbol." }); return }
        if (password != confirmPassword) { fail(I18n.t("passwordsDoNotMatch").ifEmpty { "Passwords do not match." }); return }

        msg?.textContent = "Resetting password..."
        msg?.className = ""

        RsaCryptoModule.getPublicKey()
            .then { publicKey -> RsaCryptoModule.encrypt(password, publicKey) }
            .then { encrypted ->
                apiFetch(
                    "/api/auth/reset-password?token=" + window.asDynamic().encodeURIComponent(token),
                    json("method" to "POST", "body" to JSON.stringify(json("encryptedData" to encrypted)))
                )
            }
            .then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = "Password reset successfully! You can now login."
                    (document.getElementById("password") as? HTMLInputElement)?.value = ""
                    (document.getElementById("confirm-password") as? HTMLInputElement)?.value = ""
                } else {
                    fail(result.error?.toString() ?: "Failed to reset password.")
                }
            }
            .catch { _: dynamic -> fail("Failed to reset password.") }
    }
}

@JsExport
@JsName("MagicLinkLoginPage")
object MagicLinkLoginPageModule {
    fun init() {
        val params = js("new URLSearchParams(window.location.search)")
        val token = params.get("token") as? String
        val msg = document.getElementById("message")
        if (token == null) {
            msg?.className = "message error"
            msg?.textContent = "Invalid or missing token."
            return
        }
        window.asDynamic().fetch("/api/auth/magic-link-login?token=" + window.asDynamic().encodeURIComponent(token)).then { res: dynamic ->
            res.json().then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = "Login successful! Redirecting..."
                    window.setTimeout({ window.location.href = "/" }, 1000)
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.error?.toString() ?: "Login failed. The link may be invalid or expired."
                }
            }
        }.catch { _: dynamic ->
            msg?.className = "message error"
            msg?.textContent = "Login failed."
        }
    }
}

@JsExport
@JsName("EmailChangeVerificationPage")
object EmailChangeVerificationPageModule {
    fun init() {
        val params = js("new URLSearchParams(window.location.search)")
        val token = params.get("token") as? String
        val msg = document.getElementById("message")
        if (token == null) {
            msg?.className = "message error"
            msg?.textContent = "Invalid or missing token."
            return
        }
        window.asDynamic().fetch("/api/users/verify-email-change?token=" + window.asDynamic().encodeURIComponent(token)).then { res: dynamic ->
            res.json().then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = result.message?.toString() ?: "Email changed successfully!"
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.message?.toString() ?: "Failed to change email. The link may be invalid or expired."
                }
            }
        }.catch { _: dynamic ->
            msg?.className = "message error"
            msg?.textContent = "Failed to change email."
        }
    }
}
