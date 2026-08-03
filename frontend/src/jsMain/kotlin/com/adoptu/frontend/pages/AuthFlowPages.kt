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
@JsName("EmailVerificationPage")
object EmailVerificationPageModule {
    fun init() {
        val params = js("new URLSearchParams(window.location.search)")
        val token = params.get("token") as? String
        if (token.isNullOrBlank()) {
            showError()
            return
        }
        window.asDynamic().fetch("/api/auth/verify-email?token=" + window.asDynamic().encodeURIComponent(token)).then { res: dynamic ->
            res.json().then { result: dynamic ->
                if (result.success == true) showSuccess() else showError()
            }
        }.catch { _: dynamic -> showError() }
    }

    private fun showSuccess() {
        document.getElementById("verification-success")?.className = "verification-success"
        document.getElementById("verification-error")?.className = "verification-error hidden"
        var countdown = 10
        val countdownEl = document.getElementById("countdown")
        var intervalId = -1
        intervalId = window.setInterval({
            countdown--
            countdownEl?.textContent = countdown.toString()
            if (countdown <= 0) {
                window.clearInterval(intervalId)
                window.location.href = "/"
            }
        }, 1000)
    }

    private fun showError() {
        document.getElementById("verification-error")?.className = "verification-error"
        document.getElementById("verification-success")?.className = "verification-success hidden"
    }
}

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
            msg?.textContent = I18n.t("emailRequired")
            return
        }
        msg?.textContent = I18n.t("sendingEllipsis")
        msg?.className = ""

        RsaCryptoModule.getPublicKey()
            .then { publicKey -> RsaCryptoModule.encrypt(email, publicKey) }
            .then { encrypted ->
                apiFetch("/api/auth/forgot-password", json("method" to "POST", "body" to JSON.stringify(json("encryptedData" to encrypted))))
            }
            .then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = I18n.t("resetLinkSent")
                    emailInput?.value = ""
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.error?.toString() ?: I18n.t("failedSendResetLink")
                }
            }
            .catch { _: dynamic ->
                msg?.className = "message error"
                msg?.textContent = I18n.t("failedSendResetLink")
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
                it.textContent = I18n.t("invalidOrMissingToken")
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

        if (password.length < 8) { fail(I18n.t("passwordTooShort")); return }
        if (!Regex("[A-Z]").containsMatchIn(password)) { fail(I18n.t("passwordNeedUppercase")); return }
        if (!Regex("[a-z]").containsMatchIn(password)) { fail(I18n.t("passwordNeedLowercase")); return }
        if (!Regex("[0-9]").containsMatchIn(password)) { fail(I18n.t("passwordNeedNumber")); return }
        if (!Regex("[!@#\$%^&*(),.?\":{}|<>\\-_+=/\\[\\]\\\\|°º«»¿]").containsMatchIn(password)) { fail(I18n.t("passwordNeedSymbol")); return }
        if (password != confirmPassword) { fail(I18n.t("passwordsDoNotMatch")); return }

        msg?.textContent = I18n.t("resettingPassword")
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
                    msg?.textContent = I18n.t("passwordResetSuccess")
                    (document.getElementById("password") as? HTMLInputElement)?.value = ""
                    (document.getElementById("confirm-password") as? HTMLInputElement)?.value = ""
                } else {
                    fail(result.error?.toString() ?: I18n.t("failedResetPassword"))
                }
            }
            .catch { _: dynamic -> fail(I18n.t("failedResetPassword")) }
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
            msg?.textContent = I18n.t("invalidOrMissingToken")
            return
        }
        window.asDynamic().fetch("/api/auth/magic-link-login?token=" + window.asDynamic().encodeURIComponent(token)).then { res: dynamic ->
            res.json().then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = I18n.t("loginSuccessRedirecting")
                    window.setTimeout({ window.location.href = "/" }, 1000)
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.error?.toString() ?: I18n.t("loginFailedExpiredLink")
                }
            }
        }.catch { _: dynamic ->
            msg?.className = "message error"
            msg?.textContent = I18n.t("loginFailed")
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
            msg?.textContent = I18n.t("invalidOrMissingToken")
            return
        }
        window.asDynamic().fetch("/api/users/verify-email-change?token=" + window.asDynamic().encodeURIComponent(token)).then { res: dynamic ->
            res.json().then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = result.message?.toString() ?: I18n.t("emailChangedSuccess")
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.message?.toString() ?: I18n.t("failedChangeEmailExpired")
                }
            }
        }.catch { _: dynamic ->
            msg?.className = "message error"
            msg?.textContent = I18n.t("failedChangeEmail")
        }
    }
}

@JsExport
@JsName("ProfileEmailVerificationPage")
object ProfileEmailVerificationPageModule {
    fun init() {
        val params = js("new URLSearchParams(window.location.search)")
        val token = params.get("token") as? String
        val msg = document.getElementById("message")
        if (token == null) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("invalidOrMissingToken")
            return
        }
        window.asDynamic().fetch("/api/users/verify-profile-email?token=" + window.asDynamic().encodeURIComponent(token)).then { res: dynamic ->
            res.json().then { result: dynamic ->
                if (result.success == true) {
                    msg?.className = "message success"
                    msg?.textContent = result.message?.toString() ?: I18n.t("emailVerifiedSuccess")
                } else {
                    msg?.className = "message error"
                    msg?.textContent = result.message?.toString() ?: I18n.t("failedVerifyEmailExpired")
                }
            }
        }.catch { _: dynamic ->
            msg?.className = "message error"
            msg?.textContent = I18n.t("failedVerifyEmail")
        }
    }
}
