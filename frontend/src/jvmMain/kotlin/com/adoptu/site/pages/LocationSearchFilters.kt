package com.adoptu.site.pages

import kotlinx.html.*

// The country/state/city/zip/neighborhood change-handling + localStorage persistence that used
// to be an inline <script> here now lives in CommonModule.initLocationSearchFilters() (frontend/
// Common.kt), called unconditionally from Main.kt for every page (guarded on #search-country
// existing, same as this component's own DOM lookups already were) - a static page has no
// per-response CSP nonce to stamp on an inline script, so it has to be an external file.
fun DIV.locationSearchFilters(
    formClass: String = "location-search-form",
    includeNeighborhood: Boolean = true
) {
    div(classes = formClass) {
        p(classes = "location-search-hint") {
            attributes["data-i18n"] = "selectCountryFirst"
            +"Select a country to enable filters"
        }
        div(classes = "location-search-country") {
            label { htmlFor = "search-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
            select {
                id = "search-country"
                name = "country"
                countrySelect("search-country", true, "selectCountryToSearch")
            }
        }
        div(classes = "location-search-filters") {
            div(classes = "location-search-filter") {
                label { htmlFor = "search-state"; attributes["data-i18n"] = "state"; +"State" }
                input(InputType.text) { name = "state"; id = "search-state"; disabled = true }
            }
            div(classes = "location-search-filter") {
                label { htmlFor = "search-city"; attributes["data-i18n"] = "city"; +"City" }
                input(InputType.text) { name = "city"; id = "search-city"; disabled = true }
            }
            div(classes = "location-search-filter") {
                label { htmlFor = "search-zip"; attributes["data-i18n"] = "zipCode"; +"Zip" }
                input(InputType.text) { name = "zip"; id = "search-zip"; disabled = true; maxLength = "7" }
            }
            if (includeNeighborhood) {
                div(classes = "location-search-filter") {
                    label { htmlFor = "search-neighborhood"; attributes["data-i18n"] = "neighborhood"; +"Neighborhood" }
                    input(InputType.text) { name = "neighborhood"; id = "search-neighborhood"; disabled = true }
                }
            }
        }
    }
}
