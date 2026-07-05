package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.loginPage(navParams: NavParams = NavParams()) {
    commonHead("Login - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "register-notification") {
                id = "register-notification"
                style = "display: none"
            }

            div(classes = "login-forms-grid") {
                div(classes = "auth-form-container") {
                    id = "passkey-form"
                    div(classes = "auth-form") {
                        h1 { attributes["data-i18n"] = "loginWithPasskey"; +"Login with Passkey" }
                        p { id = "passkey-message"; +"" }
                        button(classes = "btn", type = ButtonType.button) { id = "login-btn"; attributes["data-i18n"] = "signInWithPasskey"; style = "width: 100%; display: block; margin-bottom: 8px;"; +"Sign in with Passkey" }
                        button(classes = "btn btn-secondary", type = ButtonType.button) { id = "resend-btn"; attributes["data-i18n"] = "resendVerificationEmail"; style = "width: 100%; display: none;"; attributes["data-action"] = "hide-self"; +"Resend Verification Email" }
                        p { small { attributes["data-i18n"] = "usesFido"; +"Uses FIDO2 / WebAuthn for secure passwordless authentication." } }
                    }
                }

                div(classes = "auth-form-container") {
                    id = "magic-link-form"
                    div(classes = "auth-form") {
                        h1 { attributes["data-i18n"] = "loginWithMagicLink"; +"Login with Email Link" }
                        p { attributes["data-i18n"] = "magicLinkDesc"; +"We'll send you a login link to your email (valid for 5 minutes)." }
                        div(classes = "form-row") {
                            label { htmlFor = "magic-email"; attributes["data-i18n"] = "email"; +"Email" }
                            input(InputType.email) { id = "magic-email"; required = true }
                        }
                        p { id = "magic-link-message"; +"" }
                        button(classes = "btn", type = ButtonType.button) { id = "magic-link-btn"; attributes["data-i18n"] = "sendMagicLink"; +"Send Login Link" }
                    }
                }

                div(classes = "auth-form-container") {
                    id = "password-form"
                    div(classes = "auth-form") {
                        h1 { attributes["data-i18n"] = "loginWithPassword"; +"Login with Password" }
                        div(classes = "form-row") {
                            label { htmlFor = "password-email"; attributes["data-i18n"] = "email"; +"Email" }
                            input(InputType.email) { id = "password-email"; required = true }
                        }
                        div(classes = "form-row") {
                            label { htmlFor = "password-password"; attributes["data-i18n"] = "password"; +"Password" }
                            input(InputType.password) { id = "password-password"; required = true }
                        }
                        p { id = "password-login-message"; +"" }
                        button(classes = "btn", type = ButtonType.button) { id = "password-login-btn"; attributes["data-i18n"] = "signIn"; +"Sign In" }
                        p { }
                        a(href = "/forgot-password") { attributes["data-i18n"] = "forgotPassword"; +"Forgot Password?" }
                    }
                }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
