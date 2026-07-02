package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.temporalHomeProfilePage(navParams: NavParams = NavParams()) {
    commonHead("My Temporal Home - Adopt-U", "temporal-home.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "myTemporalHome"; +"My Temporal Home" }
            div(classes = "requests-section") {
                h2 { attributes["data-i18n"] = "requestsFromRescuers"; +"Requests from Rescuers" }
                div { id = "requests-container"; +"" }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}

fun HTML.temporalHomesSearchPage(navParams: NavParams = NavParams()) {
    commonHead("Find Temporal Homes - Adopt-U", "temporal-home.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "findTemporalHome"; +"Find Temporal Homes" }
            div(classes = "location-search-form") {
                locationSearchFilters(
                    includeNeighborhood = true
                )
                button(classes = "btn", type = ButtonType.button) { 
                    id = "search-btn"
                    attributes["data-i18n"] = "search"
                    +"Search" 
                }
            }
            div { id = "results-container"; classes = setOf("temporal-homes-grid"); +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}