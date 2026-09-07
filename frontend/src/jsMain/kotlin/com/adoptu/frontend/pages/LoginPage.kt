package com.adoptu.frontend.pages

import com.adoptu.frontend.I18n
import com.adoptu.frontend.WebAuthnModule
import com.adoptu.frontend.apiFetch
import com.adoptu.frontend.RsaCryptoModule
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.*
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("LoginPage")
object LoginPageModule {
    fun init() {
        I18n.loadLang(window.localStorage.getItem("preferredLanguage") ?: "en").then({ _: dynamic ->
            I18n.updatePage()
            showRegistrationNotification()
            showMagicLinkErrorNotification()
            setupPasskeyButton()
            setupMagicLinkButton()
            setupPasswordLoginButton()
            setupTabSwitching()
            setupEnterKeySubmit("magic-email", "magic-link-btn")
            setupEnterKeySubmit("password-email", "password-login-btn")
            setupEnterKeySubmit("password-password", "password-login-btn")
        })
    }

    private fun setupEnterKeySubmit(inputId: String, buttonId: String) {
        val input = document.getElementById(inputId)
        val btn = document.getElementById(buttonId) as? HTMLElement
        if (input != null && btn != null) {
            input.addEventListener("keydown", { e: dynamic ->
                if (e.key == "Enter") {
                    e.preventDefault()
                    btn.click()
                }
            })
        }
    }

    private fun showRegistrationNotification() {
        val params = window.location.search
        if (!params.contains("registered=true")) return
        val el = document.getElementById("register-notification") as? HTMLElement ?: return
        el.setAttribute("class", "register-notification")
        el.textContent = I18n.t("emailVerificationSent")
        el.style.display = "block"
    }

    // The magic-link-login redirect (AuthRoutes.kt) encodes failures as /login?error=...
    // query params; this was previously never read by the frontend, so invalid/expired/
    // banned/unverified outcomes redirected here with zero feedback to the user.
    private fun showMagicLinkErrorNotification() {
        val error: String = js("new URLSearchParams(window.location.search).get('error')") ?: return
        val resent: String? = js("new URLSearchParams(window.location.search).get('resent')")
        val el = document.getElementById("register-notification") as? HTMLElement ?: return

        val message = when (error) {
            "invalid_token" -> I18n.t("invalidToken")
            "invalid_or_expired" -> I18n.t("invalidOrExpiredMagicLink")
            "banned" -> I18n.t("accountBanned")
            "not_verified" -> if (resent == "true") I18n.t("verificationEmailResent") else I18n.t("emailNotVerifiedLogin")
            else -> return
        }

        el.setAttribute("class", "register-notification error")
        el.textContent = message
        el.style.display = "block"
    }

    private fun getPublicKey(): Promise<String> = RsaCryptoModule.getPublicKey()

    private fun setupPasskeyButton() {
        val btn = document.getElementById("login-btn")
        if (btn != null) {
            btn.addEventListener("click", {
                val messageEl = document.getElementById("passkey-message")
                messageEl?.textContent = "Requesting passkey..."
                WebAuthnModule.authenticate()
                    .then<Unit> {
                        window.location.href = "/profile"
                    }
                    .catch { error: dynamic ->
                        val errName = error?.name?.toString() ?: ""
                        messageEl?.textContent = if (errName == "NotAllowedError") {
                            "Passkey sign-in was cancelled."
                        } else {
                            "Error: ${error?.message ?: error?.toString() ?: "Unknown error"}"
                        }
                    }
            })
        }
    }

    private fun setupMagicLinkButton() {
        val btn = document.getElementById("magic-link-btn")
        if (btn != null) {
            var isProcessing = false
            btn.addEventListener("click", { e ->
                if (isProcessing) return@addEventListener
                isProcessing = true
                
                val msgEl = document.getElementById("magic-link-message")
                val emailInput = document.getElementById("magic-email") as? HTMLInputElement
                val email = emailInput?.value ?: ""

                if (email.isEmpty()) {
                    msgEl?.textContent = I18n.t("emailRequired")
                    isProcessing = false
                    return@addEventListener
                }

                msgEl?.textContent = I18n.t("sendingEllipsis")

                getPublicKey()
                    .then { publicKey -> RsaCryptoModule.encrypt(email, publicKey) }
                    .then { encrypted -> 
                        apiFetch("/api/auth/request-magic-link", js("({method: 'POST', body: JSON.stringify({encryptedData: encrypted})})"))
                    }
                    .then { data: dynamic ->
                        isProcessing = false
                        if (data.success == true) {
                            msgEl?.textContent = "Login link sent! Check your email."
                            emailInput?.value = ""
                        } else {
                            msgEl?.textContent = data.error ?: "Failed to send"
                        }
                    }
                    .catch { e: dynamic ->
                        isProcessing = false
                        msgEl?.textContent = "Error: ${e?.message ?: "Unknown"}"
                    }
            })
        }
    }

    private fun setupPasswordLoginButton() {
        val btn = document.getElementById("password-login-btn")
        if (btn != null) {
            btn.addEventListener("click", { e ->
                val msgEl = document.getElementById("password-login-message")
                val emailInput = document.getElementById("password-email") as? HTMLInputElement
                val passwordInput = document.getElementById("password-password") as? HTMLInputElement
                val email = emailInput?.value ?: ""
                val password = passwordInput?.value ?: ""

                if (email.isEmpty() || password.isEmpty()) {
                    msgEl?.textContent = "Please enter email and password"
                    return@addEventListener
                }

                msgEl?.textContent = "Signing in..."

                getPublicKey()
                    .then { publicKey -> RsaCryptoModule.encrypt("$email:$password", publicKey) }
                    .then { encrypted ->
                        apiFetch("/api/auth/login-with-password", js("({method: 'POST', body: JSON.stringify({email: email, encryptedPassword: encrypted})})"))
                    }
                    .then { data: dynamic ->
                        if (data.success == true) {
                            window.location.href = "/profile"
                        } else {
                            msgEl?.textContent = data.error ?: "Invalid credentials"
                        }
                    }
                    .catch { e: dynamic ->
                        msgEl?.textContent = "Error: ${e?.message ?: "Unknown"}"
                    }
            })
        }
    }

    private fun setupTabSwitching() {
        val hash = window.location.hash
        if (hash == "#magic-link") {
            (document.getElementById("tab-magic-link") as? HTMLElement)?.click()
        } else if (hash == "#password") {
            (document.getElementById("tab-password") as? HTMLElement)?.click()
        }
    }
}