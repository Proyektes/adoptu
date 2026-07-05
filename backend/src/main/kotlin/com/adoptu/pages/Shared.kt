package com.adoptu.pages

import com.adoptu.common.Country
import kotlinx.html.*

fun HTML.commonHead(title: String, extraCss: String? = null) {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        this.title { +title }
        link(rel = "stylesheet", href = "/static/css/style.css")
        link(rel = "icon", href = "https://static.adopt-u.org/favicon.ico", type = "image/x-icon")
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200")
        extraCss?.let { link(rel = "stylesheet", href = "/static/css/$it") }
    }
}

fun A.commonLogo() {
    classes = setOf("logo")
    img(src = "https://static.adopt-u.org/logo.svg", alt = "Adopt-U Logo")
    span { +"Adopt-U" }
}

fun BODY.commonScripts(isLoggedIn: Boolean = false) {
    script(src = "/static/js/common.js") {}
    script { unsafe { raw("window.isLoggedInGlobal = $isLoggedIn; frontend.com.adoptu.frontend.Common.initDropdowns(); frontend.com.adoptu.frontend.Common.initI18n(null); window.t = function(k) { return window.AdoptuI18n ? window.AdoptuI18n.t(k) : k; }; window.tCountry = function(n) { return window.AdoptuI18n ? window.AdoptuI18n.translateCountry(n) : n; };") } }
}

fun DIV.languageDropdown() {
    div(classes = "lang-dropdown") {
        button(classes = "lang-dropbtn", type = ButtonType.button) { id = "lang-dropbtn"; +"🌐" }
        div(classes = "lang-dropdown-content") {
            a(classes = "lang-option") { attributes["data-lang"] = "en"; +"🇺🇸 English" }
            a(classes = "lang-option") { attributes["data-lang"] = "es"; +"🇪🇸 Español" }
            a(classes = "lang-option") { attributes["data-lang"] = "fr"; +"🇫🇷 Français" }
            a(classes = "lang-option") { attributes["data-lang"] = "pt"; +"🇧🇷 Português" }
            a(classes = "lang-option") { attributes["data-lang"] = "zh"; +"🇨🇳 中文" }
        }
    }
}

fun NAV.languageDropdown() {
    div(classes = "lang-dropdown") {
        button(classes = "lang-dropbtn", type = ButtonType.button) { id = "lang-dropbtn"; +"🌐" }
        div(classes = "lang-dropdown-content") {
            a(classes = "lang-option") { attributes["data-lang"] = "en"; +"🇺🇸 English" }
            a(classes = "lang-option") { attributes["data-lang"] = "es"; +"🇪🇸 Español" }
            a(classes = "lang-option") { attributes["data-lang"] = "fr"; +"🇫🇷 Français" }
            a(classes = "lang-option") { attributes["data-lang"] = "pt"; +"🇧🇷 Português" }
            a(classes = "lang-option") { attributes["data-lang"] = "zh"; +"🇨🇳 中文" }
        }
    }
}

fun NAV.commonNav(isLoggedIn: Boolean = false, isAdmin: Boolean = false, isRescuerOrAdmin: Boolean = false, isTemporalHomeOrAdmin: Boolean = false) {
    div(classes = "nav-right") {
        if (isLoggedIn) {
            commonResourcesDropdown()
        }
        a("https://paypal.me/adoptu") { target = "_blank"; id = "nav-donate"; attributes["data-i18n"] = "donate"; +"Donate" }
        
        if (!isLoggedIn) {
            a("/login") { id = "nav-login"; attributes["data-i18n"] = "login"; +"Login" }
            a("/register") { id = "nav-register"; attributes["data-i18n"] = "register"; +"Register" }
        } else {
            div(classes = "user-menu") {
                div(classes = "user-avatar") { +"👤" }
                div(classes = "user-dropdown") {
                    a("/profile") { 
                        span { attributes["data-i18n"] = "profile"; +"Profile" }
                        span(classes = "material-symbols-outlined") { +Icons.USER }
                    }
                    if (isRescuerOrAdmin || isAdmin) {
                        a("/my-pets") {
                            span { attributes["data-i18n"] = "myPets"; +"My Pets" }
                            span(classes = "material-symbols-outlined") { +Icons.PAW }
                        }
                    }
                    if (isTemporalHomeOrAdmin || isAdmin) {
                        a("/temporal-home") {
                            span { attributes["data-i18n"] = "myTemporalHome"; +"My Temporal Home" }
                            span(classes = "material-symbols-outlined") { +Icons.HOME }
                        }
                    }
                    if (isAdmin) {
                        a("/admin") {
                            span { attributes["data-i18n"] = "admin"; +"Admin" }
                            span(classes = "material-symbols-outlined") { +Icons.SETTINGS }
                        }
                        a("/admin/shelters") {
                            span { attributes["data-i18n"] = "manageShelters"; +"Manage Shelters" }
                            span(classes = "material-symbols-outlined") { +Icons.HOME }
                        }
                    }
                    a("/logout") {
                        span { attributes["data-i18n"] = "logout"; +"Close session" }
                        span(classes = "material-symbols-outlined") { +Icons.LOGOUT }
                    }
                }
            }
        }
    }
    languageDropdown()
}

fun NAV.guestNav() {
    div(classes = "nav-right") {
        a("https://paypal.me/adoptu") { target = "_blank"; id = "nav-donate"; attributes["data-i18n"] = "donate"; +"Donate" }
        a("/login", classes = "nav-right") { id = "nav-login"; attributes["data-i18n"] = "login"; +"Login" }
        a("/register", classes = "nav-right") { id = "nav-register"; attributes["data-i18n"] = "register"; +"Register" }
        languageDropdown()
    }
}

fun DIV.commonResourcesDropdown() {
    div(classes = "resources-dropdown") {
        a(href = "#", classes = "resources-dropbtn") { 
            span { attributes["data-i18n"] = "resources"; +"Resources" }
            +" ▼"
        }
        div(classes = "resources-dropdown-content") {
            a("/shelters") { 
                span { attributes["data-i18n"] = "shelters"; +"Shelters" }
                span(classes = "material-symbols-outlined") { +Icons.SHELTER }
            }
            a("/photographers") { 
                span { attributes["data-i18n"] = "photographers"; +"Photographers" }
                span(classes = "material-symbols-outlined") { +Icons.CAMERA }
            }
            a("/sterilization-locations") { 
                span { attributes["data-i18n"] = "sterilizationLocations"; +"Sterilization Locations" }
                span(classes = "material-symbols-outlined") { +Icons.SYRINGE }
            }
            a("/temporal-homes") { 
                span { attributes["data-i18n"] = "findTemporalHomes"; +"Temporal Homes" }
                span(classes = "material-symbols-outlined") { +Icons.HOME }
            }
        }
    }
}

fun SELECT.countrySelect(id: String, includeSelectOption: Boolean = true, i18nKey: String = "selectCountry") {
    if (includeSelectOption) {
        option { value = ""; attributes["data-i18n"] = i18nKey }
    }
    Country.entries.forEach { country ->
        option { this.value = country.displayName; attributes["data-i18n"] = country.i18nKey; +country.displayName }
    }
}

fun BODY.footer() {
    footer {
        a("/privacy") { attributes["data-i18n"] = "privacyPolicy"; +"Privacy Policy" }
        span { +" | " }
        a("/terms") { attributes["data-i18n"] = "termsConditions"; +"Terms and Conditions" }
        span { +" | " }
        span { +"© 2025 Adopt-U" }
    }
}
