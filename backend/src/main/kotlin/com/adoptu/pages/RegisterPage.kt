package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.registerPage(navParams: NavParams = NavParams()) {
    commonHead("Register - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                id="auth-form"
                classes = setOf("auth-form", "register-simple")
                h1 {
                    attributes["data-i18n"] = "registerNewAccount";
                    classes = setOf("text-center")
                    +"Create Account"
                }
                
                form { id = "register-form"
                    h2 { attributes["data-i18n"] = "accountDetails"; +"Account Details" }
                    label { htmlFor = "email"; attributes["data-i18n"] = "email"; +"Email" }; input(InputType.email) { name = "email"; id = "email"; required = true }
                    label { htmlFor = "displayName"; attributes["data-i18n"] = "displayName"; +"Display Name" }; input(InputType.text) { name = "displayName"; id = "displayName"; required = true }
                    
                    label { attributes["data-i18n"] = "selectAdditionalRoles"; +"Select additional roles:" }
                    div(classes = "checkbox-group") {
                        div {
                            classes = setOf("checkbox-row")
                            input(InputType.checkBox) { name = "roles"; value = "ADOPTER"; id = "role-adopter"; checked = true; disabled = true }
                            span { attributes["data-i18n"] = "adoptPet"; +"Adopt a pet" }
                            +" (required)"
                        }
                        div {
                            classes = setOf("checkbox-row")
                            input(InputType.checkBox) { name = "roles"; value = "RESCUER"; id = "role-rescuer" }
                            span { attributes["data-i18n"] = "publishPets"; +"Publish pets for adoption" }
                        }
                        div {
                            classes = setOf("checkbox-row")
                            input(InputType.checkBox) { name = "roles"; value = "PHOTOGRAPHER"; id = "role-photographer" }
                            span { attributes["data-i18n"] = "offerPhotography"; +"Offer photography services" }
                        }
                        div {
                            classes = setOf("checkbox-row")
                            input(InputType.checkBox) { name = "roles"; value = "TEMPORAL_HOME"; id = "role-temporal-home" }
                            span { attributes["data-i18n"] = "provideTemporaryHome"; +"Provide temporary home for pets" }
                        }
                    }
                    
                    h2 { attributes["data-i18n"] = "registrationMethod"; +"Registration Method" }
                    
                    div(classes = "method-selection") {
                        p { classes = setOf("hint-text")
                            attributes["data-i18n"] = "selectLoginMethods"
                            +"Select one or more login methods:" }
                        div(classes = "checkbox-row-with-info") {
                            label {
                                classes = setOf("checkbox-row", "clickable")
                                input(InputType.checkBox) { name = "method"; value = "passkey"; id = "method-passkey"; checked = true }
                                span { attributes["data-i18n"] = "registerPasskeyOption"; +"Passkey (most secure, works on all your devices)" }
                            }
                            button(type = ButtonType.button, classes = "info-icon-btn") {
                                id = "passkey-info-btn"
                                attributes["aria-label"] = "How passkeys work"
                                +"?"
                            }
                        }
                        label {
                            classes = setOf("checkbox-row", "clickable")
                            input(InputType.checkBox) { name = "method"; value = "password"; id = "method-password" }
                            span { attributes["data-i18n"] = "registerPasswordOption"; +"Password (less secure, use as backup only)" }
                        }
                    }
                    
                    div(classes = "password-fields") {
                        attributes["id"] = "password-fields"
                        div(classes = "form-row") {
                            label { htmlFor = "password"; attributes["data-i18n"] = "password"; +"Password" }
                            input(InputType.password) { id = "password"; name = "password" }
                        }
                        div(classes = "form-row") {
                            label { htmlFor = "confirmPassword"; attributes["data-i18n"] = "confirmPassword"; +"Confirm Password" }
                            input(InputType.password) { id = "confirmPassword"; name = "confirmPassword" }
                        }
                        div(classes = "password-requirements") {
                            p { id = "password-requirements-text"; attributes["data-i18n"] = "passwordRequirements"; +"Password must be at least 8 characters and contain uppercase, lowercase, number, and symbol" }
                            div { id = "password-checks"; +"" }
                        }
                    }
                    
                    p { id = "message"; +"" }
                    button(classes = "btn", type = ButtonType.submit) {
                        id="register-button"
                        attributes["data-i18n"] = "register"; +"Register"
                    }
                }
                
                div(classes = "form-actions") {
                    p { classes = setOf("login-link-row"); attributes["data-i18n"] = "alreadyHaveAccount"; +"Already have an account?" }
                    a(href = "/login") { button(classes = "btn btn-secondary", type = ButtonType.button) {
                        id="register-page-login"
                        attributes["data-i18n"] = "login"; +"Login"
                    } }
                }

                div(classes = "form-modal hidden") {
                    id = "passkey-info-modal"
                    div(classes = "form-modal-content card-bg") {
                        h2 { attributes["data-i18n"] = "howPasskeysWorkTitle"; +"How does a passkey work?" }
                        div(classes = "passkey-info-body") {
                            p { attributes["data-i18n"] = "howPasskeysWorkIntro"
                                +"A passkey replaces your password with your device's built-in security — fingerprint, face recognition, or screen lock PIN." }
                            p { attributes["data-i18n"] = "howPasskeysWorkSecurity"
                                +"It's stored only on your device, can't be phished or leaked in a data breach, and syncs automatically across your devices." }
                            p { attributes["data-i18n"] = "howPasskeysWorkBackup"
                                +"You can add a password too, but a passkey alone is enough to sign in — no password needed." }
                        }
                        div(classes = "form-actions") {
                            button(type = ButtonType.button) {
                                id = "passkey-info-close"
                                classes = setOf("btn", "btn-secondary")
                                attributes["data-i18n"] = "gotIt"
                                +"Got it"
                            }
                        }
                    }
                }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}