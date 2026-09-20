package com.adoptu.site.pages

import kotlinx.html.*

fun HTML.logoutPage(navParams: NavParams = NavParams()) {
    commonHead("Signing out - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            p { attributes["data-i18n"] = "signingOut"; +"Signing out..." }
        }
        footer()
        commonScripts()
    }
}
