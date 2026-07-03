package com.adoptu.pages

import com.adoptu.routes.NavParams
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
                h1 { +"Forgot Password" }
                p { +"Enter your email and we'll send you a link to reset your password." }
                div(classes = "form-row") {
                    label { htmlFor = "email"; +"Email" }
                    input(InputType.email) { id = "email"; required = true }
                }
                p { id = "message"; +"" }
                button(classes = "btn", type = ButtonType.button) { id = "submit-btn"; +"Send Reset Link" }
                p { }
                a(href = "/login") { +"Back to Login" }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
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
                h1 { +"Reset Password" }
                p { +"Enter your new password below." }
                div(classes = "form-row") {
                    label { htmlFor = "password"; +"New Password" }
                    input(InputType.password) { id = "password"; required = true; minLength = "8" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "confirm-password"; +"Confirm Password" }
                    input(InputType.password) { id = "confirm-password"; required = true; minLength = "8" }
                }
                p { id = "message"; +"" }
                button(classes = "btn", type = ButtonType.button) { id = "submit-btn"; +"Reset Password" }
                p { }
                a(href = "/login") { +"Back to Login" }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
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
                h1 { +"Email Link Login" }
                p { id = "message"; +"Verifying..." }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
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
                h1 { +"Email Change" }
                p { id = "message"; +"Verifying..." }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
