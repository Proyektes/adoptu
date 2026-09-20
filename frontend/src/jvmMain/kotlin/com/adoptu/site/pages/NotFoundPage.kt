package com.adoptu.site.pages

import kotlinx.html.*

fun HTML.notFoundPage(navParams: NavParams = NavParams()) {
    commonHead("Page Not Found - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "pets-empty-state not-found-state") {
                h1 { attributes["data-i18n"] = "pageNotFoundTitle"; +"Page not found" }
                p { attributes["data-i18n"] = "pageNotFoundHint"; +"The link may be old or mistyped. The pets are still here." }
                a("/", classes = "btn") { attributes["data-i18n"] = "browsePets"; +"Browse pets" }
            }
        }
        footer()
        commonScripts()
    }
}
