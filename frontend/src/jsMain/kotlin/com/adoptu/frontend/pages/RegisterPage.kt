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
            hideMessage(messageEl)
            setupMethodToggle()
            setupPasskeyInfoModal()
            setupForm()
        })
    }

    private fun showError(messageEl: HTMLElement?, text: String) {
        messageEl?.textContent = text
        messageEl?.setAttribute("class", "message error")
        messageEl?.style?.display = "block"
    }

    private fun showStatus(messageEl: HTMLElement?, text: String) {
        messageEl?.textContent = text
        messageEl?.setAttribute("class", "message")
        messageEl?.style?.display = "block"
    }

    private fun hideMessage(messageEl: HTMLElement?) {
        messageEl?.setAttribute("class", "message")
        messageEl?.style?.display = "none"
    }

    private fun setupMethodToggle() {
        val passkeyCheckbox = document.getElementById("method-passkey") as? HTMLInputElement
        val passwordCheckbox = document.getElementById("method-password") as? HTMLInputElement

        val passwordFields = document.getElementById("password-fields") as? HTMLElement

        fun updateVisibility() {
            if (passwordFields != null) {
                val showPassword = passwordCheckbox?.checked == true
                passwordFields.style.display = if (showPassword) "block" else "none"
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

    private fun setupPasskeyInfoModal() {
        val modal = document.getElementById("passkey-info-modal") as? HTMLElement
        val infoBtn = document.getElementById("passkey-info-btn") as? HTMLElement
        val closeBtn = document.getElementById("passkey-info-close") as? HTMLElement

        infoBtn?.addEventListener("click", { _ ->
            modal?.style?.display = "flex"
        })

        closeBtn?.addEventListener("click", { _ ->
            modal?.style?.display = "none"
        })

        modal?.addEventListener("click", { e ->
            if (e.target == modal) {
                modal.style.display = "none"
            }
        })
    }

    private fun setupForm() {
        console.log("RegisterPage.setupForm() called")
        val form = document.getElementById("register-form") as? HTMLFormElement
        val messageEl = document.getElementById("message") as? HTMLElement

        form?.addEventListener("submit", { e ->
            console.log("RegisterPage form submitted")
            e.preventDefault()
            hideMessage(messageEl)

            val emailInput = document.getElementById("email") as? HTMLInputElement
            val displayNameInput = document.getElementById("displayName") as? HTMLInputElement
            val passkeyCheckbox = document.getElementById("method-passkey") as? HTMLInputElement
            val passwordCheckbox = document.getElementById("method-password") as? HTMLInputElement

            val email = emailInput?.value ?: ""
            val displayName = displayNameInput?.value ?: ""
            val usePasskey = passkeyCheckbox?.checked == true
            val usePassword = passwordCheckbox?.checked == true

            if (email.isEmpty() || displayName.isEmpty()) {
                showError(messageEl, "Please enter email and display name")
                return@addEventListener
            }

            if (!usePasskey && !usePassword) {
                showError(messageEl, "Please select at least one login method")
                return@addEventListener
            }

            if (usePassword) {
                val passwordInput = document.getElementById("password") as? HTMLInputElement
                val confirmPasswordInput = document.getElementById("confirmPassword") as? HTMLInputElement

                val password = passwordInput?.value ?: ""
                val confirmPassword = confirmPasswordInput?.value ?: ""

                if (password.isEmpty()) {
                    showError(messageEl, "Please enter a password")
                    return@addEventListener
                }

                if (password != confirmPassword) {
                    showError(messageEl, "Passwords do not match")
                    return@addEventListener
                }

                if (password.length < 8) {
                    showError(messageEl, "Password must be at least 8 characters")
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

    private fun registerPasskey(email: String, displayName: String, messageEl: HTMLElement?) {
        showStatus(messageEl, "Creating passkey...")
        WebAuthnModule.register(email, displayName)
            .then { _: dynamic ->
                console.log("Registration successful")
                window.location.href = "/login?registered=true"
            }
            .catch { error: dynamic ->
                console.log("Registration error: $error")
                val errMsg = error?.message ?: error?.toString() ?: "Unknown error"
                showError(messageEl, "$errMsg")
            }
    }

    private fun registerPassword(email: String, displayName: String, messageEl: HTMLElement?) {
        val passwordInput = document.getElementById("password") as? HTMLInputElement
        val confirmPasswordInput = document.getElementById("confirmPassword") as? HTMLInputElement

        val password = passwordInput?.value ?: ""
        val confirmPassword = confirmPasswordInput?.value ?: ""

        if (password.isEmpty()) {
            showError(messageEl, "Please enter a password")
            return
        }

        if (password != confirmPassword) {
            showError(messageEl, "Passwords do not match")
            return
        }

        if (password.length < 8) {
            showError(messageEl, "Password must be at least 8 characters")
            return
        }

        showStatus(messageEl, "Registering...")

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
                showError(messageEl, "$errMsg")
            }
    }

    private fun registerBoth(email: String, displayName: String, messageEl: HTMLElement?) {
        val passwordInput = document.getElementById("password") as? HTMLInputElement
        val confirmPasswordInput = document.getElementById("confirmPassword") as? HTMLInputElement

        val password = passwordInput?.value ?: ""
        val confirmPassword = confirmPasswordInput?.value ?: ""

        if (password.isEmpty()) {
            showError(messageEl, "Please enter a password")
            return
        }

        if (password != confirmPassword) {
            showError(messageEl, "Passwords do not match")
            return
        }

        if (password.length < 8) {
            showError(messageEl, "Password must be at least 8 characters")
            return
        }

        showStatus(messageEl, "Creating passkey and password...")

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
                        showError(messageEl, "$errMsg")
                    }
            }
            .catch { error: dynamic ->
                val errMsg = error?.message ?: error?.toString() ?: "Unknown error"
                showError(messageEl, "$errMsg")
            }
    }

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