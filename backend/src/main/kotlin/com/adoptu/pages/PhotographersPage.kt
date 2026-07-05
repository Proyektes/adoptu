package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

private const val SEARCH_FILTER = "search-filter"

fun HTML.photographersPage(navParams: NavParams = NavParams()) {
    commonHead("Photographers - Adopt-U", "photographers.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "photographers"; +"Photographers" }
            p { attributes["data-i18n"] = "photographerDescription"; +"Professional photographers offering pet photo sessions" }
            div(classes = "location-search-form") {
                locationSearchFilters(
                    includeNeighborhood = true
                )
                button(classes = "btn", type = ButtonType.button) { 
                    id = "search-btn"
                    attributes["data-i18n"] = "search"; 
                    onClick = "searchPhotographers()"; 
                    +"Search" 
                }
            }
            div { id = "photographers"; classes = setOf("photographer-grid"); +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
