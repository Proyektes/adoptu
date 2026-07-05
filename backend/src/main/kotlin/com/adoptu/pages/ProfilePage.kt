package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.profilePage(navParams: NavParams = NavParams()) {
    commonHead("Profile - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "profile-header card-bg sticky-section") {
                h1 { attributes["data-i18n"] = "profile"; +"Profile" }
                div(classes = "profile-header-actions") {
                    div(classes = "message") { id = "message"; +"" }
                    button(classes = "btn", type = ButtonType.button) { id = "save-profile-btn"; attributes["data-i18n"] = "save"; +"Save" }
                }
            }

            div(classes = "card-bg profile-section") {
                h2 { attributes["data-i18n"] = "accountSettings"; +"Account Settings" }
                div(classes = "form-row") {
                    label { htmlFor = "email"; attributes["data-i18n"] = "email"; +"Email" }
                    input(InputType.email) { id = "email"; disabled = true }
                }
                div(classes = "form-row") {
                    label { htmlFor = "displayName"; attributes["data-i18n"] = "displayName"; +"Display Name" }
                    input(InputType.text) { id = "displayName"; required = true }
                    span(classes = "field-error") { id = "displayName-error" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "language"; attributes["data-i18n"] = "language"; +"Language" }
                    select { id = "language"; name = "language"
                        option { value = "en"; +"English 🇺🇸" }
                        option { value = "es"; +"Español 🇪🇸" }
                        option { value = "fr"; +"Français 🇫🇷" }
                        option { value = "pt"; +"Português 🇧🇷" }
                        option { value = "zh"; +"中文 🇨🇳" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                    select { id = "country"; countrySelect("country", true) }
                    span(classes = "field-error") { id = "country-error" }
                }
                div(classes = "form-row") {
                    label { attributes["data-i18n"] = "yourRoles"; +"Your Roles:" }
                    div(classes = "roles-section") {
                        div(classes = "checkbox-group") {
                            div {
                                classes = setOf("checkbox-row")
                                input(InputType.checkBox) { id = "role-adopter"; checked = true; disabled = true }
                                span { attributes["data-i18n"] = "adopterRequired"; +"Adopter (required)" }
                            }
                            div {
                                classes = setOf("checkbox-row")
                                input(InputType.checkBox) { id = "role-rescuer" }
                                span { attributes["data-i18n"] = "publishPets"; +"Rescuer - Publish pets for adoption" }
                            }
                            div {
                                classes = setOf("checkbox-row")
                                input(InputType.checkBox) { id = "role-photographer" }
                                span { attributes["data-i18n"] = "offerPhotography"; +"Photographer - Offer photography services" }
                            }
                            div {
                                classes = setOf("checkbox-row")
                                input(InputType.checkBox) { id = "role-temporal-home" }
                                span { attributes["data-i18n"] = "provideTemporaryHome"; +"Temporal Home - Provide temporary home for pets" }
                            }
                            div {
                                classes = setOf("checkbox-row")
                                input(InputType.checkBox) { id = "role-shelter" }
                                span { attributes["data-i18n"] = "provideShelter"; +"Shelter - Manage an animal shelter" }
                            }
                            div {
                                classes = setOf("checkbox-row")
                                input(InputType.checkBox) { id = "role-sterilization" }
                                span { attributes["data-i18n"] = "provideSterilization"; +"Sterilization Service - Provide sterilization services" }
                            }
                        }
                    }
                }
            }

            div(classes = "card-bg profile-section shelter-section hidden") {
                h2 { attributes["data-i18n"] = "shelterSettings"; +"Shelter Settings" }
                div(classes = "form-row") {
                    label { htmlFor = "shelter-name"; attributes["data-i18n"] = "name"; +"Shelter Name" }
                    input(InputType.text) { id = "shelter-name" }
                    span(classes = "field-error") { id = "shelter-name-error" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "shelter-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                    select { id = "shelter-country"; countrySelect("shelter-country", false) }
                    span(classes = "field-error") { id = "shelter-country-error" }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "shelter-state"; attributes["data-i18n"] = "state"; +"State" }
                        input(InputType.text) { id = "shelter-state" }
                    }
                    div {
                        label { htmlFor = "shelter-city"; attributes["data-i18n"] = "city"; +"City" }
                        input(InputType.text) { id = "shelter-city" }
                        span(classes = "field-error") { id = "shelter-city-error" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "shelter-address"; attributes["data-i18n"] = "address"; +"Address" }
                    input(InputType.text) { id = "shelter-address" }
                    span(classes = "field-error") { id = "shelter-address-error" }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "shelter-zip"; attributes["data-i18n"] = "zipCode"; +"Zip Code" }
                        input(InputType.text) { id = "shelter-zip" }
                    }
                    div {
                        label { htmlFor = "shelter-phone"; attributes["data-i18n"] = "phone"; +"Phone" }
                        input(InputType.tel) { id = "shelter-phone" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "shelter-email"; attributes["data-i18n"] = "email"; +"Email" }
                    input(InputType.email) { id = "shelter-email" }
                    span(classes = "field-hint") { id = "shelter-email-status" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "shelter-website"; attributes["data-i18n"] = "website"; +"Website" }
                    input(InputType.url) { id = "shelter-website" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "shelter-description"; attributes["data-i18n"] = "description"; +"Description" }
                    textArea { id = "shelter-description"; rows = "3" }
                }
            }

            div(classes = "card-bg profile-section sterilization-section hidden") {
                h2 { attributes["data-i18n"] = "sterilizationSettings"; +"Sterilization Service Settings" }
                div(classes = "form-row") {
                    label { htmlFor = "sterilization-name"; attributes["data-i18n"] = "name"; +"Name" }
                    input(InputType.text) { id = "sterilization-name" }
                    span(classes = "field-error") { id = "sterilization-name-error" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "sterilization-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                    select { id = "sterilization-country"; countrySelect("sterilization-country", false) }
                    span(classes = "field-error") { id = "sterilization-country-error" }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "sterilization-state"; attributes["data-i18n"] = "state"; +"State" }
                        input(InputType.text) { id = "sterilization-state" }
                    }
                    div {
                        label { htmlFor = "sterilization-city"; attributes["data-i18n"] = "city"; +"City" }
                        input(InputType.text) { id = "sterilization-city" }
                        span(classes = "field-error") { id = "sterilization-city-error" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "sterilization-address"; attributes["data-i18n"] = "address"; +"Address" }
                    input(InputType.text) { id = "sterilization-address" }
                    span(classes = "field-error") { id = "sterilization-address-error" }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "sterilization-zip"; attributes["data-i18n"] = "zipCode"; +"Zip Code" }
                        input(InputType.text) { id = "sterilization-zip" }
                    }
                    div {
                        label { htmlFor = "sterilization-phone"; attributes["data-i18n"] = "phone"; +"Phone" }
                        input(InputType.tel) { id = "sterilization-phone" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "sterilization-email"; attributes["data-i18n"] = "email"; +"Email" }
                    input(InputType.email) { id = "sterilization-email" }
                    span(classes = "field-hint") { id = "sterilization-email-status" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "sterilization-website"; attributes["data-i18n"] = "website"; +"Website" }
                    input(InputType.url) { id = "sterilization-website" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "sterilization-description"; attributes["data-i18n"] = "description"; +"Description" }
                    textArea { id = "sterilization-description"; rows = "3" }
                }
            }

            div(classes = "card-bg profile-section photographer-section hidden") {
                h2 { attributes["data-i18n"] = "photographerSettings"; +"Photographer Settings" }
                div(classes = "form-row") {
                    label { htmlFor = "photographerCountry"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                    select { id = "photographerCountry"; countrySelect("photographerCountry", false) }
                    span(classes = "field-error") { id = "photographerCountry-error" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "photographerState"; attributes["data-i18n"] = "state"; +"State" }
                    input(InputType.text) { id = "photographerState" }
                    span(classes = "field-error") { id = "photographerState-error" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "photographerFee"; attributes["data-i18n"] = "sessionFee"; +"Session Fee" }
                    div(classes = "fee-input-group") {
                        input(InputType.number) { id = "photographerFee"; step = "0.01"; value = "0"; this.min = "0" }
                        select { id = "photographerCurrency"
                            option { value = "USD"; +"$ USD" }
                            option { value = "EUR"; +"€ EUR" }
                            option { value = "GBP"; +"£ GBP" }
                            option { value = "CAD"; +"$ CAD" }
                            option { value = "AUD"; +"$ AUD" }
                        }
                    }
                }
            }

            div(classes = "card-bg profile-section temporal-home-section hidden") {
                h2 { attributes["data-i18n"] = "temporalHomeSettings"; +"Temporal Home Settings" }
                div(classes = "form-row") {
                    label { htmlFor = "th-alias"; attributes["data-i18n"] = "alias"; +"Alias" }
                    input(InputType.text) { id = "th-alias" }
                    span(classes = "field-error") { id = "th-alias-error" }
                }
                div(classes = "form-row") {
                    label { htmlFor = "th-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                    select { id = "th-country"; countrySelect("th-country", false) }
                    span(classes = "field-error") { id = "th-country-error" }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "th-state"; attributes["data-i18n"] = "state"; +"State" }
                        input(InputType.text) { id = "th-state" }
                    }
                    div {
                        label { htmlFor = "th-city"; attributes["data-i18n"] = "city"; +"City" }
                        input(InputType.text) { id = "th-city" }
                        span(classes = "field-error") { id = "th-city-error" }
                    }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "th-zip"; attributes["data-i18n"] = "zipCode"; +"Zip Code" }
                        input(InputType.text) { id = "th-zip" }
                    }
                    div {
                        label { htmlFor = "th-neighborhood"; attributes["data-i18n"] = "neighborhood"; +"Neighborhood" }
                        input(InputType.text) { id = "th-neighborhood" }
                    }
                }
            }

            div(classes = "card-bg profile-section") {
                h2 { attributes["data-i18n"] = "securitySettings"; +"Security Settings" }
                div(classes = "form-row") {
                    id = "password-section"
                    p { id = "password-status"; +"" }
                }
                div(classes = "form-row") {
                    id = "current-password-row"
                    classes = setOf("form-row", "password-hidden")
                    label { htmlFor = "current-password"; attributes["data-i18n"] = "currentPassword"; +"Current Password" }
                    input(InputType.password) { id = "current-password"; disabled = true }
                }
                div(classes = "form-row") {
                    label { htmlFor = "new-password"; attributes["data-i18n"] = "newPassword"; +"New Password" }
                    input(InputType.password) { id = "new-password"; }
                }
                div(classes = "form-row") {
                    label { htmlFor = "confirm-password"; attributes["data-i18n"] = "confirmPassword"; +"Confirm Password" }
                    input(InputType.password) { id = "confirm-password"; }
                }
                p { id = "password-message"; +"" }
                button(classes = "btn btn-secondary", type = ButtonType.button) { id = "save-password-btn"; attributes["data-i18n"] = "savePassword"; +"Save Password" }
            }

            div(classes = "card-bg profile-section") {
                h2 { attributes["data-i18n"] = "passkeySettings"; +"Passkey Settings" }
                p { attributes["data-i18n"] = "passkeyDesc"; +"Add additional passkeys to your account for easy login across devices." }
                p { id = "passkey-status"; +"Loading..." }
                div(classes = "form-row") {
                    label { htmlFor = "passkey-name"; attributes["data-i18n"] = "passkeyName"; +"Passkey Name" }
                    input(InputType.text) { id = "passkey-name"; placeholder = "e.g., MacBook, iPhone, YubiKey" }
                }
                p { id = "passkey-message"; +"" }
                button(classes = "btn", type = ButtonType.button) { id = "register-passkey-btn"; attributes["data-i18n"] = "registerWithPasskey"; +"Register with Passkey" }
            }

            div(classes = "card-bg profile-section") {
                h2 { attributes["data-i18n"] = "emailChange"; +"Change Email" }
                p { attributes["data-i18n"] = "emailChangeDesc"; +"Update your email address. A verification link will be sent to the new email." }
                div(classes = "form-row") {
                    label { htmlFor = "new-email"; attributes["data-i18n"] = "newEmail"; +"New Email" }
                    input(InputType.email) { id = "new-email"; required = true }
                }
                p { id = "email-change-message"; +"" }
                button(classes = "btn btn-secondary", type = ButtonType.button) { id = "change-email-btn"; attributes["data-i18n"] = "requestEmailChange"; +"Request Email Change" }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
