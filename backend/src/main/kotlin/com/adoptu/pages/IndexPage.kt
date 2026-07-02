package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.indexPage(navParams: NavParams = NavParams()) {
    commonHead("Browse Pets - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "petsForAdoption"; +"Pets for Adoption" }
            div(classes = "location-search-form") {
                p(classes = "location-search-hint") { id = "pets-country-hint"; attributes["data-i18n"] = "selectCountryFirst"; +"Select a country to enable filters" }
                div(classes = "location-search-country") {
                    label { htmlFor = "pets-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                    select {
                        id = "pets-country"
                        name = "country"
                        countrySelect("pets-country", true, "selectCountryToSearch")
                    }
                }
            }
            div { id = "pets-error"; classes = setOf("error-message"); style = "display:none" }
            div { id = "pets-filters"; style = "display:none"
                div(classes = "filter-buttons") {
                    button(classes = "filter-btn active", type = ButtonType.button) { attributes["data-type"] = ""; attributes["data-i18n"] = "all"; +"All" }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "DOG"; +"🐕 Dogs" }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "CAT"; +"🐱 Cats" }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "BIRD"; +"🐦 Birds" }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "FISH"; +"🐟 Fish" }
                }
                div(classes = "filter-buttons") {
                    select(classes = "filter-sex") {
                        option { value = ""; +"All Sex" }
                        option { value = "MALE"; +"♂ Male" }
                        option { value = "FEMALE"; +"♀ Female" }
                    }
                }
            }
            div { id = "pets"; classes = setOf("pet-grid"); +"" }
            div { id = "pets-empty"; classes = setOf("pets-empty-state"); style = "display:none" }
            div { id = "pets-sentinel"; classes = setOf("pets-sentinel") }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
