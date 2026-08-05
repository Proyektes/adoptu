package com.adoptu.site.pages

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
            div { id = "pets-error"; classes = setOf("error-message", "hidden") }
            // Guests can browse fine, but a saved search needs a standing account to notify -
            // same data-auth gating pattern as every other user-only control (see Shared.kt).
            button(classes = "btn btn-secondary hidden", type = ButtonType.button) {
                id = "save-search-btn"
                attributes["data-auth"] = "user"
                attributes["data-i18n"] = "saveThisSearch"
                +"Save this search"
            }
            p { id = "save-search-message"; +"" }
            div { id = "pets-filters"; classes = setOf("hidden")
                div(classes = "filter-buttons") {
                    button(classes = "filter-btn active", type = ButtonType.button) { attributes["data-type"] = ""; attributes["data-i18n"] = "all"; +"All" }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "DOG"
                        +"🐕 "; span { attributes["data-i18n"] = "filterDogs"; +"Dogs" } }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "CAT"
                        +"🐱 "; span { attributes["data-i18n"] = "filterCats"; +"Cats" } }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "BIRD"
                        +"🐦 "; span { attributes["data-i18n"] = "filterBirds"; +"Birds" } }
                    button(classes = "filter-btn", type = ButtonType.button) { attributes["data-type"] = "FISH"
                        +"🐟 "; span { attributes["data-i18n"] = "filterFish"; +"Fish" } }
                }
                div(classes = "filter-buttons") {
                    select(classes = "filter-sex") {
                        option { value = ""; attributes["data-i18n"] = "filterAllSexOption"; +"All Sex" }
                        option { value = "MALE"; attributes["data-i18n"] = "filterMaleOption"; +"♂ Male" }
                        option { value = "FEMALE"; attributes["data-i18n"] = "filterFemaleOption"; +"♀ Female" }
                    }
                }
            }
            div { id = "pets"; classes = setOf("pet-grid"); +"" }
            div { id = "pets-empty"; classes = setOf("pets-empty-state") }
            div { id = "pets-sentinel"; classes = setOf("pets-sentinel") }
        }
        footer()
        commonScripts()
    }
}
