package com.adoptu.pages

import com.adoptu.routes.NavParams
import com.adoptu.web.CspNonce
import kotlinx.html.*

fun HTML.emailVerificationPage(success: Boolean, language: String = "en", navParams: NavParams = NavParams()) {
    commonHead("Email Verification - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "verification-container") {
                if (success) {
                    div(classes = "verification-success") {
                        h1(classes = "success-title") {
                            attributes["data-i18n"] = "emailVerifiedTitle"
                            +"Email Verified Successfully!"
                        }
                        div(classes = "success-icon") {
                            unsafe { raw("""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="green" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>""") }
                        }
                        p(classes = "success-message") {
                            attributes["data-i18n"] = "emailVerifiedMessage"
                            +"Your email has been verified. Your registration is now complete."
                        }
                        p(classes = "redirect-message") {
                            attributes["data-i18n"] = "redirectingIn"
                            +"Redirecting to the main page in "
                        }
                        span(classes = "countdown") { id = "countdown"; +"10" }
                        span { +"..." }
                        div(classes = "login-prompt") {
                            p {
                                attributes["data-i18n"] = "canAlsoLogin"
                                +"You can also "
                            }
                            a("/login") {
                                attributes["data-i18n"] = "loginNow"
                                +"login now"
                            }
                        }
                    }
                    script {
                        attributes["nonce"] = CspNonce.current()
                        unsafe { raw("""
                            (function() {
                                var countdown = 10;
                                var countdownEl = document.getElementById('countdown');
                                var interval = setInterval(function() {
                                    countdown--;
                                    if (countdownEl) countdownEl.textContent = countdown;
                                    if (countdown <= 0) {
                                        clearInterval(interval);
                                        window.location.href = '/';
                                    }
                                }, 1000);
                            })();
                        """.trimIndent()) }
                    }
                } else {
                    div(classes = "verification-error") {
                        h1(classes = "error-title") {
                            attributes["data-i18n"] = "emailVerificationFailed"
                            +"Verification Failed"
                        }
                        div(classes = "error-icon") {
                            unsafe { raw("""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="red" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>""") }
                        }
                        p(classes = "error-message") {
                            attributes["data-i18n"] = "invalidOrExpiredToken"
                            +"The verification link is invalid or has expired."
                        }
                        p(classes = "try-again") {
                            a("/register") {
                                attributes["data-i18n"] = "tryRegisteringAgain"
                                +"Please try registering again."
                            }
                        }
                    }
                }
            }
        }
        footer()
        script(src = "/static/js/common.js") { attributes["nonce"] = CspNonce.current() }
        script {
            attributes["nonce"] = CspNonce.current()
            unsafe { raw("window.isLoggedInGlobal = false; frontend.com.adoptu.frontend.Common.initI18n(null);") }
        }
    }
}
