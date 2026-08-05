package com.adoptu.site.pages

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
            div(classes = "requests-section") {
                h2 { attributes["data-i18n"] = "petsCurrentlyWithMe"; +"Pets Currently With Me" }
                div { id = "foster-placements-container"; +"" }
            }
        }
        footer()
        commonScripts()
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
        commonScripts()
    }
}

// Moved out of UIRoutes.kt (was defined inline there, the only page that wasn't already in this
// package). data-arg (the block token) can't be baked in at build time like the rest of this
// page's markup - TemporalHomeBlockPageModule.init() (frontend/pages/TemporalHomePage.kt) reads
// ?token= from the URL at runtime and sets it on #block-btn before CommonModule's delegated
// data-action click handler can use it.
fun HTML.temporalHomeBlockPage() {
    commonHead("Block Rescuer - Adopt-U")
    body {
        header { a("/") { commonLogo() } }
        main {
            // No data-i18n here, matching this page's pre-existing (inline, in UIRoutes.kt)
            // behavior - I18n.t() silently returns the raw key string on a dictionary miss, so
            // adding a data-i18n attribute for a key that isn't in every language's map would
            // make the text WORSE (a literal key like "blockRescuer" on screen) than plain English.
            h1 { +"Report as Spam & Block Rescuer" }
            p { +"Are you sure you want to block this rescuer from sending you more requests?" }
            button(type = ButtonType.button) {
                id = "block-btn"
                attributes["data-action"] = "blockRescuerAndRedirect"
                +"Block Rescuer"
            }
        }
        commonScripts()
    }
}

fun HTML.temporalHomeDetailPage(navParams: NavParams = NavParams()) {
    commonHead("Temporal Home Details - Adopt-U", "temporal-home.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div { id = "temporal-home-detail"; classes = setOf("temporal-home-detail"); +"" }
            div { id = "message"; +"" }
        }
        footer()
        commonScripts()
    }
}