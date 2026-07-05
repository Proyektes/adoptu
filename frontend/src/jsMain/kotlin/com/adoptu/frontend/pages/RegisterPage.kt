package com.adoptu.frontend.pages

import com.adoptu.frontend.I18n
import com.adoptu.frontend.RsaCryptoModule
import com.adoptu.frontend.WebAuthnModule
import com.adoptu.frontend.apiFetch
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.*
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("RegisterPage")
object RegisterPageModule {
    private var selectedMethod = "passkey"

    fun init() {
        console.log("RegisterPage.init() called")
        I18n.loadLang(window.localStorage.getItem("preferredLanguage") ?: "en").then({ _: dynamic ->
            console.log("RegisterPage.init() after i18n load")
            I18n.updatePage()
            val messageEl = document.getElementById("message") as? HTMLElement
            messageEl?.setAttribute("style", "display: none")
            setupMethodToggle()
            setupForm()
        })
    }

    private fun setupMethodToggle() {
        val passkeyCheckbox = document.getElementById("method-passkey") as? HTMLInputElement
        val passwordCheckbox = document.getElementById("method-password") as? HTMLInputElement

        val passwordFields = document.getElementById("password-fields")

        fun updateVisibility() {
            if (passwordFields != null) {
                val showPassword = passwordCheckbox?.checked == true
                passwordFields.setAttribute("style", "display: ${if (showPassword) "block" else "none"}")
            }
        }

        fun ensureAtLeastOne() {
            if (passkeyCheckbox != null && passwordCheckbox != null) {
                if (!passkeyCheckbox.checked && !passwordCheckbox.checked) {
                    passkeyCheckbox.checked = true
                }
            }
        }

        passkeyCheckbox?.addEventListener("click", { _ ->
            ensureAtLeastOne()
            updateVisibility()
        })

        passwordCheckbox?.addEventListener("click", { _ ->
            ensureAtLeastOne()
            updateVisibility()
        })

        updateVisibility()
    }

    private fun setupForm() {
        console.log("RegisterPage.setupForm() called")
        val form = document.getElementById("register-form") as? HTMLFormElement
        val messageEl = document.getElementById("message") as? HTMLElement

        form?.addEventListener("submit", { e ->
            console.log("RegisterPage form submitted")
            e.preventDefault()
            messageEl?.setAttribute("style", "display: none")

            val emailInput = document.getElementById("email") as? HTMLInputElement
            val displayNameInput = document.getElementById("displayName") as? HTMLInputElement
            val passkeyCheckbox = document.getElementById("method-passkey") as? HTMLInputElement
            val passwordCheckbox = document.getElementById("method-password") as? HTMLInputElement

            val email = emailInput?.value ?: ""
            val displayName = displayNameInput?.value ?: ""
            val usePasskey = passkeyCheckbox?.checked == true
            val usePassword = passwordCheckbox?.checked == true

            if (email.isEmpty() || displayName.isEmpty()) {
                messageEl?.textContent = "Please enter email and display name"
                messageEl?.setAttribute("style", "display: block")
                return@addEventListener
            }

            if (!usePasskey && !usePassword) {
                messageEl?.textContent = "Please select at least one login method"
                messageEl?.setAttribute("style", "display: block")
                return@addEventListener
            }

            if (usePassword) {
                val passwordInput = document.getElementById("password") as? HTMLInputElement
                val confirmPasswordInput = document.getElementById("confirmPassword") as? HTMLInputElement

                val password = passwordInput?.value ?: ""
                val confirmPassword = confirmPasswordInput?.value ?: ""

                if (password.isEmpty()) {
                    messageEl?.textContent = "Please enter a password"
                    messageEl?.setAttribute("style", "display: block")
                    return@addEventListener
                }

                if (password != confirmPassword) {
                    messageEl?.textContent = "Passwords do not match"
                    messageEl?.setAttribute("style", "display: block")
                    return@addEventListener
                }

                if (password.length < 8) {
                    messageEl?.textContent = "Password must be at least 8 characters"
                    messageEl?.setAttribute("style", "display: block")
                    return@addEventListener
                }
            }

            when {
                usePasskey && usePassword -> registerBoth(email, displayName, messageEl)
                usePasskey -> registerPasskey(email, displayName, messageEl)
                usePassword -> registerPassword(email, displayName, messageEl)
            }
        })
    }

    private fun registerPasskey(email: String, displayName: String, messageEl: Element?) {
        messageEl?.textContent = "Creating passkey..."
        messageEl?.setAttribute("style", "display: block")
        WebAuthnModule.register(email, displayName)
            .then { _: dynamic ->
                console.log("Registration successful")
                window.location.href = postRegisterRedirect()
            }
            .catch { error: dynamic ->
                console.log("Registration error: $error")
                val errMsg = error?.message ?: error?.toString() ?: "Unknown error"
                messageEl?.textContent = "$errMsg"
                messageEl?.setAttribute("style", "display: block")
            }
    }

    private fun registerPassword(email: String, displayName: String, messageEl: Element?) {
        val passwordInput = document.getElementById("password") as? HTMLInputElement
        val confirmPasswordInput = document.getElementById("confirmPassword") as? HTMLInputElement

        val password = passwordInput?.value ?: ""
        val confirmPassword = confirmPasswordInput?.value ?: ""

        if (password.isEmpty()) {
            messageEl?.textContent = "Please enter a password"
            messageEl?.setAttribute("style", "display: block")
            return
        }

        if (password != confirmPassword) {
            messageEl?.textContent = "Passwords do not match"
            messageEl?.setAttribute("style", "display: block")
            return
        }

        if (password.length < 8) {
            messageEl?.textContent = "Password must be at least 8 characters"
            messageEl?.setAttribute("style", "display: block")
            return
        }

        messageEl?.textContent = "Registering..."
        messageEl?.setAttribute("style", "display: block")

        RsaCryptoModule.getPublicKey()
            .then { publicKey ->
                RsaCryptoModule.encrypt("$email:$password", publicKey)
            }
            .then { encryptedPassword ->
                val body = json(
                    "email" to email,
                    "displayName" to displayName,
                    "roles" to getRoles(),
                    "encryptedPassword" to encryptedPassword.unsafeCast<String>()
                )
                apiFetch("/api/auth/register-password", js("({method: 'POST', body: JSON.stringify(body)})"))
            }
            .then { _: dynamic ->
                window.location.href = postRegisterRedirect()
            }
            .catch { error: dynamic ->
                val errMsg = error?.message ?: error?.toString() ?: "Unknown error"
                messageEl?.textContent = "$errMsg"
                messageEl?.setAttribute("style", "display: block")
            }
    }

    private fun registerBoth(email: String, displayName: String, messageEl: Element?) {
        val passwordInput = document.getElementById("password") as? HTMLInputElement
        val confirmPasswordInput = document.getElementById("confirmPassword") as? HTMLInputElement

        val password = passwordInput?.value ?: ""
        val confirmPassword = confirmPasswordInput?.value ?: ""

        if (password.isEmpty()) {
            messageEl?.textContent = "Please enter a password"
            messageEl?.setAttribute("style", "display: block")
            return
        }

        if (password != confirmPassword) {
            messageEl?.textContent = "Passwords do not match"
            messageEl?.setAttribute("style", "display: block")
            return
        }

        if (password.length < 8) {
            messageEl?.textContent = "Password must be at least 8 characters"
            messageEl?.setAttribute("style", "display: block")
            return
        }

        messageEl?.textContent = "Creating passkey and password..."
        messageEl?.setAttribute("style", "display: block")

        WebAuthnModule.register(email, displayName)
            .then<Unit> {
                RsaCryptoModule.getPublicKey()
                    .then { publicKey ->
                        RsaCryptoModule.encrypt("$email:$password", publicKey)
                    }
                    .then { encryptedPassword ->
                        val body = json(
                            "email" to email,
                            "displayName" to displayName,
                            "roles" to getRoles(),
                            "encryptedPassword" to encryptedPassword.unsafeCast<String>()
                        )
                        apiFetch("/api/auth/register-password", js("({method: 'POST', body: JSON.stringify(body)})"))
                    }
                    .then { _: dynamic ->
                        window.location.href = "/login?registered=true"
                    }
                    .catch { error: dynamic ->
                        val errMsg = error?.message ?: error?.toString() ?: "Unknown error"
                        messageEl?.textContent = "$errMsg"
                        messageEl?.setAttribute("style", "display: block")
                    }
            }
            .catch { error: dynamic ->
                val errMsg = error?.message ?: error?.toString() ?: "Unknown error"
                messageEl?.textContent = "$errMsg"
                messageEl?.setAttribute("style", "display: block")
            }
    }

    // Photographer/temporal-home/shelter/sterilization accounts have role-specific
    // fields to fill in on /profile; plain adopter/rescuer accounts don't, so they
    // go through the normal verify-then-login flow instead.
    private fun needsProfileCompletion(): Boolean {
        val ids = listOf("role-photographer", "role-temporal-home", "role-shelter", "role-sterilization")
        return ids.any { (document.getElementById(it) as? HTMLInputElement)?.checked == true }
    }

    private fun postRegisterRedirect(): String =
        if (needsProfileCompletion()) "/profile" else "/login?registered=true"

    private fun getRoles(): String {
        val roles = mutableListOf("ADOPTER")
        if ((document.getElementById("role-rescuer") as? HTMLInputElement)?.checked == true) roles.add("RESCUER")
        if ((document.getElementById("role-photographer") as? HTMLInputElement)?.checked == true) roles.add("PHOTOGRAPHER")
        if ((document.getElementById("role-temporal-home") as? HTMLInputElement)?.checked == true) roles.add("TEMPORAL_HOME")
        if ((document.getElementById("role-shelter") as? HTMLInputElement)?.checked == true) roles.add("SHELTER")
        if ((document.getElementById("role-sterilization") as? HTMLInputElement)?.checked == true) roles.add("STERILIZATION_SERVICE")
        return roles.joinToString(",")
    }
}