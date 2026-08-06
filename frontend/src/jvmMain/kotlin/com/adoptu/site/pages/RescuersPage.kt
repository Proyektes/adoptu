package com.adoptu.site.pages

import kotlinx.html.*

fun HTML.rescuersPage(navParams: NavParams = NavParams()) {
    commonHead("Find Rescuers - Adopt-U", "rescuers.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "rescuerDirectory"; +"Find Rescuers" }
            div { id = "rescuers-container"; classes = setOf("rescuers-grid"); +"" }
        }
        footer()
        commonScripts()
    }
}

// Same pattern as temporal-home-detail: id is read from window.location.pathname client-side
// (RescuerDetailPageModule), not baked into the HTML - see SiteGenerator.kt's rewrites().
fun HTML.rescuerDetailPage(navParams: NavParams = NavParams()) {
    commonHead("Rescuer Details - Adopt-U", "rescuers.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div { id = "rescuer-detail"; classes = setOf("rescuer-detail"); +"" }
        }
        footer()
        commonScripts()
    }
}
