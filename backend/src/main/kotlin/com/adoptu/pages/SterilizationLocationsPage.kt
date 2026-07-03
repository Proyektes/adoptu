package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.sterilizationLocationsPage(navParams: NavParams = NavParams()) {
    commonHead("Sterilization Locations - Adopt-U", "sterilization.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "sterilizationLocations"; +"Sterilization Locations" }
            p { attributes["data-i18n"] = "sterilizationLocationsDescription"; +"Find places where you can take animals to be sterilized." }
            
            div(classes = "location-search-form") {
                locationSearchFilters(
                    includeNeighborhood = true
                )
                button(classes = "btn", type = ButtonType.button) { 
                    id = "search-btn"
                    attributes["data-i18n"] = "search"; 
                    onClick = "searchLocations()"; 
                    +"Search" 
                }
            }
            
            div { id = "locations-container"; +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}

fun HTML.adminSterilizationLocationsPage(navParams: NavParams = NavParams()) {
    commonHead("Manage Sterilization Locations - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div {
                h1 { attributes["data-i18n"] = "adminSterilizationLocations"; +"Manage Sterilization Locations" }
                a("/sterilization-locations") { attributes["data-i18n"] = "viewPublic"; +"View Public Page" }
            }
            div { id = "message"; +"" }
            
            button(classes = "btn") {
                id = "add-btn"
                attributes["data-i18n"] = "addLocation"
                onClick = "showForm()"
                +"Add Location"
            }
            
            div(classes = "form-modal") {
                id = "form-modal"
                style = "display: none;"
                div(classes = "form-modal-content card-bg") {
                    h2 { attributes["data-i18n"] = "addEditLocation"; +"Add/Edit Location" }
                    form(classes = "auth-form") {
                        id = "location-form"
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "name"; +"Name" }
                            input(type = InputType.text) { name = "name"; required = true }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "country"; +"Country" }
                            select { name = "country"; required = true; id = "form-country" }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "state"; +"State" }
                            input(type = InputType.text) { name = "state" }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "city"; +"City" }
                            input(type = InputType.text) { name = "city"; required = true }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "address"; +"Address" }
                            input(type = InputType.text) { name = "address"; required = true }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "zip"; +"ZIP" }
                            input(type = InputType.text) { name = "zip" }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "phone"; +"Phone" }
                            input(type = InputType.tel) { name = "phone" }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "email"; +"Email" }
                            input(type = InputType.email) { name = "email" }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "website"; +"Website" }
                            input(type = InputType.url) { name = "website" }
                        }
                        div(classes = "form-row") {
                            label { attributes["data-i18n"] = "description"; +"Description" }
                            textArea { name = "description"; rows = "4" }
                        }
                        div(classes = "form-actions") {
                            button(type = ButtonType.submit) {
                                classes = setOf("btn")
                                attributes["data-i18n"] = "save"
                                +"Save"
                            }
                            button(type = ButtonType.button) {
                                classes = setOf("btn", "btn-secondary")
                                attributes["data-i18n"] = "cancel"
                                onClick = "hideForm()"
                                +"Cancel"
                            }
                        }
                    }
                }
            }
            
            div { id = "locations-container"; +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
