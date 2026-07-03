package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.sheltersPage(navParams: NavParams = NavParams()) {
    commonHead("Animal Shelters - Adopt-U", "shelters.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "animalShelters"; +"Animal Shelters" }
            p { attributes["data-i18n"] = "sheltersDescription"; +"Find animal shelters where you can find pets to adopt or leave them by prior agreement with the shelter" }
            div(classes = "location-search-form") {
                locationSearchFilters(
                    includeNeighborhood = true
                )
                button(classes = "btn", type = ButtonType.button) { 
                    id = "search-btn"
                    attributes["data-i18n"] = "searchShelters"; 
                    onClick = "searchShelters()"; 
                    +"Search" 
                }
            }
            div { id = "shelters-error"; classes = setOf("error-message"); style = "display:none" }
            div { id = "shelters"; classes = setOf("shelter-grid"); +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
