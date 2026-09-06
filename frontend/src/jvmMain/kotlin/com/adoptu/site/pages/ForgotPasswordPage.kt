package com.adoptu.site.pages

import kotlinx.html.*

fun HTML.forgotPasswordPage(navParams: NavParams = NavParams()) {
    commonHead("Forgot Password - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                id = "auth-form"
                classes = setOf("auth-form")
                h1 { attributes["data-i18n"] = "forgotPasswordTitle"; +"Forgot Password" }
                p { attributes["data-i18n"] = "forgotPasswordIntro"; +"Enter your email and we'll send you a link to reset your password." }
                div(classes = "form-row") {
                    label { htmlFor = "email"; attributes["data-i18n"] = "email"; +"Email" }
                    input(InputType.email) { id = "email"; required = true }
                }
                p { id = "message"; +"" }
                button(classes = "btn full-width-btn", type = ButtonType.button) { id = "submit-btn"; attributes["data-i18n"] = "sendResetLink"; +"Send Reset Link" }
                p { }
                a(href = "/login") { button(classes = "btn btn-secondary full-width-btn", type = ButtonType.button) { attributes["data-i18n"] = "backToLogin"; +"Back to Login" } }
            }
        }
        footer()
        commonScripts()
    }
}

fun HTML.resetPasswordPage(navParams: NavParams = NavParams()) {
    commonHead("Reset Password - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                id = "auth-form"
                classes = setOf("auth-form")
                h1 { attributes["data-i18n"] = "resetPasswordTitle"; +"Reset Password" }
                p { attributes["data-i18n"] = "resetPasswordIntro"; +"Enter your new password below." }
                div(classes = "form-row") {
                    label { htmlFor = "password"; attributes["data-i18n"] = "newPassword"; +"New Password" }
                    input(InputType.password) { id = "password"; required = true; minLength = "8" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "confirm-password"; attributes["data-i18n"] = "confirmPassword"; +"Confirm Password" }
                    input(InputType.password) { id = "confirm-password"; required = true; minLength = "8" }
                }
                p { id = "message"; +"" }
                button(classes = "btn full-width-btn", type = ButtonType.button) { id = "submit-btn"; attributes["data-i18n"] = "resetPasswordTitle"; +"Reset Password" }
                p { }
                a(href = "/login") { button(classes = "btn btn-secondary full-width-btn", type = ButtonType.button) { attributes["data-i18n"] = "backToLogin"; +"Back to Login" } }
            }
        }
        footer()
        commonScripts()
    }
}

fun HTML.magicLinkLoginPage(navParams: NavParams = NavParams()) {
    commonHead("Magic Link Login - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                id = "auth-form"
                classes = setOf("auth-form")
                h1 { attributes["data-i18n"] = "emailLinkLoginTitle"; +"Email Link Login" }
                p { id = "message"; attributes["data-i18n"] = "verifying"; +"Verifying..." }
            }
        }
        footer()
        commonScripts()
    }
}

fun HTML.emailChangeVerificationPage(navParams: NavParams = NavParams()) {
    commonHead("Email Change - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                id = "auth-form"
                classes = setOf("auth-form")
                h1 { attributes["data-i18n"] = "emailChangeTitle"; +"Email Change" }
                p { id = "message"; attributes["data-i18n"] = "verifying"; +"Verifying..." }
            }
        }
        footer()
        commonScripts()
    }
}

fun HTML.profileEmailVerificationPage(navParams: NavParams = NavParams()) {
    commonHead("Verify Contact Email - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                id = "auth-form"
                classes = setOf("auth-form")
                h1 { attributes["data-i18n"] = "verifyContactEmailTitle"; +"Verify Contact Email" }
                p { id = "message"; attributes["data-i18n"] = "verifying"; +"Verifying..." }
            }
        }
        footer()
        commonScripts()
    }
}
