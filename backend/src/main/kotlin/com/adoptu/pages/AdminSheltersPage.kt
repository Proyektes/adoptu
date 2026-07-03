package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.adminSheltersPage(navParams: NavParams = NavParams()) {
    commonHead("Manage Shelters - Adopt-U", "shelters.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "manageShelters"; +"Manage Shelters" }
            
            div(classes = "filter-form") {
                div(classes = "search-country") {
                    label { htmlFor = "filter-country"; attributes["data-i18n"] = "country"; +"Country" }
                    select {
                        id = "filter-country"
                        name = "country"
                        countrySelect("filter-country", true, "selectCountryToFilter")
                    }
                }
                div(classes = "search-state") {
                    label { htmlFor = "filter-state"; attributes["data-i18n"] = "state"; +"State" }
                    select { id = "filter-state"; name = "state" }
                }
            }
            div { id = "message"; +"" }
            div { id = "shelters"; classes = setOf("shelter-list"); +"" }

            form(classes = "auth-form") {
                id = "shelter-form"
                h2 { attributes["data-i18n"] = "addEditShelter"; +"Add / Edit Shelter" }
                input(InputType.hidden) { id = "shelter-id" }
                div(classes = "form-row") {
                    label { htmlFor = "name"; attributes["data-i18n"] = "name"; +"Name" }
                    input(InputType.text) { id = "name"; name = "name"; required = true }
                }
                div(classes = "form-row") {
                    label { htmlFor = "country"; attributes["data-i18n"] = "country"; +"Country" }
                    select {
                        id = "country"
                        name = "country"
                        required = true
                        countrySelect("country", false)
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "state"; attributes["data-i18n"] = "state"; +"State" }
                    input(InputType.text) { id = "state"; name = "state" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "city"; attributes["data-i18n"] = "city"; +"City" }
                    input(InputType.text) { id = "city"; name = "city"; required = true }
                }
                div(classes = "form-row") {
                    label { htmlFor = "address"; attributes["data-i18n"] = "address"; +"Address" }
                    input(InputType.text) { id = "address"; name = "address"; required = true }
                }
                div(classes = "form-row") {
                    label { htmlFor = "zip"; attributes["data-i18n"] = "zip"; +"ZIP" }
                    input(InputType.text) { id = "zip"; name = "zip" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "phone"; attributes["data-i18n"] = "phone"; +"Phone" }
                    input(InputType.tel) { id = "phone"; name = "phone" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "email"; attributes["data-i18n"] = "email"; +"Email" }
                    input(InputType.email) { id = "email"; name = "email" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "website"; attributes["data-i18n"] = "website"; +"Website" }
                    input(InputType.url) { id = "website"; name = "website" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "fiscalId"; attributes["data-i18n"] = "fiscalId"; +"Fiscal ID" }
                    input(InputType.text) { id = "fiscalId"; name = "fiscalId" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "bankName"; attributes["data-i18n"] = "bankName"; +"Bank Name" }
                    input(InputType.text) { id = "bankName"; name = "bankName" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "accountHolderName"; attributes["data-i18n"] = "accountHolder"; +"Account Holder" }
                    input(InputType.text) { id = "accountHolderName"; name = "accountHolderName" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "accountNumber"; attributes["data-i18n"] = "accountNumber"; +"Account Number" }
                    input(InputType.text) { id = "accountNumber"; name = "accountNumber" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "iban"; +"IBAN" }
                    input(InputType.text) { id = "iban"; name = "iban" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "swiftBic"; +"SWIFT/BIC" }
                    input(InputType.text) { id = "swiftBic"; name = "swiftBic" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "currency"; attributes["data-i18n"] = "currency"; +"Currency" }
                    select {
                        id = "currency"
                        name = "currency"
                        listOf("USD", "EUR", "GBP", "CAD", "AUD").forEach { c -> option { value = c; +c } }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "description"; attributes["data-i18n"] = "description"; +"Description" }
                    textArea { id = "description"; name = "description"; rows = "4" }
                }
                div(classes = "form-actions") {
                    button(type = ButtonType.submit) { id = "submit-btn"; classes = setOf("btn"); attributes["data-i18n"] = "addShelter"; +"Add Shelter" }
                    button(type = ButtonType.button) { id = "cancel-btn"; classes = setOf("btn", "btn-secondary"); style = "display:none"; attributes["data-i18n"] = "cancel"; +"Cancel" }
                }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}