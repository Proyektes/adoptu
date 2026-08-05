package com.adoptu.site.pages

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
        // Generic site-wide share preview - every page except /pet/{id} gets this (that one route
        // has its own real per-pet og:image via the CloudFront-Function-routed
        // GET /api/share/pet/{id} bot path - see infra/cloudfront-functions/site-rewrite.js).
        // og-image.png doesn't exist yet - upload a real ~1200x630 image at that path.
        meta { attributes["property"] = "og:type"; attributes["content"] = "website" }
        meta { attributes["property"] = "og:title"; attributes["content"] = title }
        meta { attributes["property"] = "og:description"; attributes["content"] = "Adopt-U: pets in need of loving homes." }
        meta { attributes["property"] = "og:image"; attributes["content"] = "https://static.adopt-u.org/og-image.png" }
        meta { name = "twitter:card"; content = "summary_large_image" }
    }
}

fun A.commonLogo() {
    classes = setOf("logo")
    img(src = "https://static.adopt-u.org/logo.svg", alt = "Adopt-U Logo")
    span { +"Adopt-U" }
}

// No inline <script> here (and so no CSP nonce needed) - every page loads this one external
// bundle, and everything the old inline bootstrap used to do (initDropdowns/initI18n, the
// window.t/tCountry aliases, and now the /api/auth/me-driven nav auth-state toggle) runs
// unconditionally from Main.kt's main() instead, since a static page has no per-request server
// state left to inject here.
fun BODY.commonScripts() {
    script(src = "/static/js/common.js") {}
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

// Static HTML always includes every auth-state branch (no server session to decide with at
// render time); each gated element carries a data-auth attribute ("guest"/"user"/"admin"/
// "rescuer"/"temporal-home") that CommonModule.initAuthNav() (frontend/Common.kt) resolves
// against GET /api/auth/me after the page loads, hiding whichever branches don't apply. The
// isLoggedIn/isAdmin/... params are kept only so every existing call site (indexPage etc.)
// doesn't need to change - they're no longer read here.
fun NAV.commonNav(isLoggedIn: Boolean = false, isAdmin: Boolean = false, isRescuerOrAdmin: Boolean = false, isTemporalHomeOrAdmin: Boolean = false) {
    div(classes = "nav-right") {
        div(classes = "hidden") { attributes["data-auth"] = "user"; commonResourcesDropdown() }
        // Not data-auth gated - anonymous bystanders reporting a pet in danger is a core
        // requirement (see UrgentRescueService/SubmitUrgentReportRequest), so this must be
        // visible to guests too.
        a("/report-urgent") { id = "nav-report-urgent"; attributes["data-i18n"] = "reportUrgent"; +"Report Urgent" }
        a("/urgent-rescuer-leaderboard") { id = "nav-urgent-leaderboard"; attributes["data-i18n"] = "topRescuers"; +"Top Rescuers" }
        // Same reasoning as the urgent-report link above - anonymous lost/found reporting must
        // stay visible to guests.
        a("/report-lost-found") { id = "nav-report-lost-found"; attributes["data-i18n"] = "reportLostFound"; +"Lost & Found" }
        a("https://paypal.me/adoptu") { target = "_blank"; id = "nav-donate"; attributes["data-i18n"] = "donate"; +"Donate" }

        a("/login", classes = "hidden") { attributes["data-auth"] = "guest"; id = "nav-login"; attributes["data-i18n"] = "login"; +"Login" }
        a("/register", classes = "hidden") { attributes["data-auth"] = "guest"; id = "nav-register"; attributes["data-i18n"] = "register"; +"Register" }

        div(classes = "user-menu hidden") {
            attributes["data-auth"] = "user"
            div(classes = "user-avatar") { +"👤" }
            div(classes = "user-dropdown") {
                a("/profile") {
                    span { attributes["data-i18n"] = "profile"; +"Profile" }
                    span(classes = "material-symbols-outlined") { +Icons.USER }
                }
                a("/my-pets", classes = "hidden") {
                    attributes["data-auth"] = "rescuer"
                    span { attributes["data-i18n"] = "myPets"; +"My Pets" }
                    span(classes = "material-symbols-outlined") { +Icons.PAW }
                }
                a("/temporal-home", classes = "hidden") {
                    attributes["data-auth"] = "temporal-home"
                    span { attributes["data-i18n"] = "myTemporalHome"; +"My Temporal Home" }
                    span(classes = "material-symbols-outlined") { +Icons.HOME }
                }
                a("/urgent-rescuer-profile") {
                    span { attributes["data-i18n"] = "urgentRescuerSettings"; +"Urgent Rescuer Settings" }
                    span(classes = "material-symbols-outlined") { +Icons.URGENT }
                }
                a("/urgent-rescuer-dashboard", classes = "hidden") {
                    attributes["data-auth"] = "urgent-rescuer"
                    span { attributes["data-i18n"] = "urgentRescueAlerts"; +"Urgent Rescue Alerts" }
                    span(classes = "material-symbols-outlined") { +Icons.URGENT }
                }
                a("/admin", classes = "hidden") {
                    attributes["data-auth"] = "admin"
                    span { attributes["data-i18n"] = "admin"; +"Admin" }
                    span(classes = "material-symbols-outlined") { +Icons.SETTINGS }
                }
                a("/admin/shelters", classes = "hidden") {
                    attributes["data-auth"] = "admin"
                    span { attributes["data-i18n"] = "manageShelters"; +"Manage Shelters" }
                    span(classes = "material-symbols-outlined") { +Icons.HOME }
                }
                a("/logout") {
                    span { attributes["data-i18n"] = "logout"; +"Close session" }
                    span(classes = "material-symbols-outlined") { +Icons.LOGOUT }
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
