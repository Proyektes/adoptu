package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.petDetailPage(navParams: NavParams = NavParams()) {
    commonHead("Pet Details - Adopt-U", "pet-detail.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div { id = "pet-detail"; classes = setOf("pet-detail"); +"" }
            div { id = "message"; +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
