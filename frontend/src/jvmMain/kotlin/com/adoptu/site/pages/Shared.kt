package com.adoptu.site.pages

import com.adoptu.common.Country
import kotlinx.html.*

fun HTML.commonHead(title: String, extraCss: String? = null) {
    lang = "en"
    head {
        meta(charset = "UTF-8")
        meta(name = "description", content = "Free pet adoption, urgent rescue paging, and lost & found reporting.")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        this.title { +title }
        link(rel = "stylesheet", href = "/static/css/style.css")
        link(rel = "icon", href = "https://static.adopt-u.org/favicon.ico", type = "image/x-icon")
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200")
        extraCss?.let { link(rel = "stylesheet", href = "/static/css/$it") }
        // PWA: manifest + icons (generated from logo.svg, see frontend/pwa/) + the theme-color
        // that browser chrome (status bar, task switcher) picks up once installed. Service worker
        // itself is registered from CommonModule.kt (no inline <script> here, matching the
        // no-CSP-nonce convention below).
        link(rel = "manifest", href = "/manifest.webmanifest")
        link(rel = "apple-touch-icon", href = "/apple-touch-icon.png")
        meta(name = "theme-color", content = "#0d9488")
        // iOS ignores manifest.webmanifest's display/name for "Add to Home Screen" on older
        // versions - these are its own equivalent (standalone mode, status bar style, home
        // screen label instead of the per-page <title>).
        meta { name = "apple-mobile-web-app-capable"; content = "yes" }
        meta { name = "apple-mobile-web-app-status-bar-style"; content = "black-translucent" }
        meta { name = "apple-mobile-web-app-title"; content = "Adopt-U" }
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
            a(classes = "lang-option") { attributes["data-lang"] = "ar"; +"🇸🇦 العربية" }
            a(classes = "lang-option") { attributes["data-lang"] = "ru"; +"🇷🇺 Русский" }
            a(classes = "lang-option") { attributes["data-lang"] = "ko"; +"🇰🇷 한국어" }
            a(classes = "lang-option") { attributes["data-lang"] = "ja"; +"🇯🇵 日本語" }
            a(classes = "lang-option") { attributes["data-lang"] = "sw"; +"🇹🇿 Kiswahili" }
            a(classes = "lang-option") { attributes["data-lang"] = "ha"; +"🇳🇬 Hausa" }
            a(classes = "lang-option") { attributes["data-lang"] = "tr"; +"🇹🇷 Türkçe" }
            a(classes = "lang-option") { attributes["data-lang"] = "ro"; +"🇷🇴 Română" }
            a(classes = "lang-option") { attributes["data-lang"] = "hi"; +"🇮🇳 हिन्दी" }
            a(classes = "lang-option") { attributes["data-lang"] = "bn"; +"🇧🇩 বাংলা" }
            a(classes = "lang-option") { attributes["data-lang"] = "mr"; +"🇮🇳 मराठी" }
            a(classes = "lang-option") { attributes["data-lang"] = "te"; +"🇮🇳 తెలుగు" }
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
            a(classes = "lang-option") { attributes["data-lang"] = "ar"; +"🇸🇦 العربية" }
            a(classes = "lang-option") { attributes["data-lang"] = "ru"; +"🇷🇺 Русский" }
            a(classes = "lang-option") { attributes["data-lang"] = "ko"; +"🇰🇷 한국어" }
            a(classes = "lang-option") { attributes["data-lang"] = "ja"; +"🇯🇵 日本語" }
            a(classes = "lang-option") { attributes["data-lang"] = "sw"; +"🇹🇿 Kiswahili" }
            a(classes = "lang-option") { attributes["data-lang"] = "ha"; +"🇳🇬 Hausa" }
            a(classes = "lang-option") { attributes["data-lang"] = "tr"; +"🇹🇷 Türkçe" }
            a(classes = "lang-option") { attributes["data-lang"] = "ro"; +"🇷🇴 Română" }
            a(classes = "lang-option") { attributes["data-lang"] = "hi"; +"🇮🇳 हिन्दी" }
            a(classes = "lang-option") { attributes["data-lang"] = "bn"; +"🇧🇩 বাংলা" }
            a(classes = "lang-option") { attributes["data-lang"] = "mr"; +"🇮🇳 मराठी" }
            a(classes = "lang-option") { attributes["data-lang"] = "te"; +"🇮🇳 తెలుగు" }
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
    // Not data-auth gated - anonymous bystanders reporting a pet in danger, or a lost/found pet,
    // are core requirements (see UrgentRescueService/SubmitUrgentReportRequest and LostFoundPage),
    // so both must be visible to guests too. Stacked together in .nav-pill-stack (fixed to the
    // bottom-left, left curve bled off the viewport edge) so they stay reachable while scrolling
    // without crowding the nav row.
    div(classes = "nav-pill-stack") {
        a("/report-urgent") {
            id = "nav-report-urgent"
            classes = setOf("nav-urgent-pill")
            attributes["aria-label"] = "Report a pet in danger"
            attributes["data-i18n-aria-label"] = "reportUrgent"
            span(classes = "material-symbols-outlined") { +Icons.WARNING }
            span { attributes["data-i18n"] = "reportUrgent"; +"Urgent" }
        }
        a("/lost-found") {
            id = "nav-report-lost-found"
            classes = setOf("nav-urgent-pill")
            attributes["aria-label"] = "Report a lost or found pet"
            attributes["data-i18n-aria-label"] = "reportLostFound"
            unsafe {
                // Paw-in-lens: the real Material Symbols "pets" glyph path (same source as
                // Icons.PAW) remapped from its native 0,-960,960,960 viewBox into a 34x34 box
                // centered inside a 22px-radius lens ring, with a shortened handle - see icon
                // exploration in chat.
                raw(
                    """<svg width="24" height="24" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false"><circle cx="26" cy="26" r="22" fill="none" stroke="currentColor" stroke-width="3"/><path transform="translate(9,43) scale(0.0354167)" fill="currentColor" d="M180-475q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Zm180-160q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Zm240 0q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Zm180 160q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29ZM266-75q-45 0-75.5-34.5T160-191q0-52 35.5-91t70.5-77q29-31 50-67.5t50-68.5q22-26 51-43t63-17q34 0 63 16t51 42q28 32 49.5 69t50.5 69q35 38 70.5 77t35.5 91q0 47-30.5 81.5T694-75q-54 0-107-9t-107-9q-54 0-107 9t-107 9Z"/><line x1="41.6" y1="41.6" x2="51.5" y2="51.5" stroke="currentColor" stroke-width="4" stroke-linecap="round"/></svg>"""
                )
            }
            span { attributes["data-i18n"] = "reportLostFound"; +"Lost/Found" }
        }
    }
    div(classes = "nav-right") {
        div { commonResourcesDropdown() }
        a("/urgent-rescuer-leaderboard") {
            id = "nav-urgent-leaderboard"
            classes = setOf("nav-icon-only")
            attributes["title"] = "Top Rescuers"
            span(classes = "material-symbols-outlined") { +Icons.TROPHY }
            span(classes = "visually-hidden") { attributes["data-i18n"] = "topRescuers"; +"Top Rescuers" }
        }
        // Below 600px (see _layout.scss), #nav-urgent-leaderboard hides and this hamburger takes
        // over - same dropdown click pattern as .resources-dropdown, just gated by media query
        // instead of always visible.
        div(classes = "hamburger-menu") {
            button(classes = "hamburger-btn", type = ButtonType.button) {
                id = "hamburger-btn"
                attributes["aria-label"] = "Menu"
                span(classes = "material-symbols-outlined") { +Icons.MENU }
            }
            div(classes = "hamburger-dropdown-content") {
                a("/urgent-rescuer-leaderboard") {
                    span(classes = "material-symbols-outlined") { +Icons.TROPHY }
                    span { attributes["data-i18n"] = "topRescuers"; +"Top Rescuers" }
                }
            }
        }
        a("/login", classes = "btn hidden") { attributes["data-auth"] = "guest"; id = "nav-login"; attributes["data-i18n"] = "login"; +"Login" }
        a("/register", classes = "btn hidden") { attributes["data-auth"] = "guest"; id = "nav-register"; attributes["data-i18n"] = "register"; +"Register" }

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
                    span { attributes["data-i18n"] = "manage"; +"Manage" }
                    span(classes = "material-symbols-outlined") { +Icons.SETTINGS }
                }
                a("/logout") {
                    span { attributes["data-i18n"] = "logout"; +"Close session" }
                    span(classes = "material-symbols-outlined") { +Icons.LOGOUT }
                }
            }
        }
    }
    a("https://paypal.me/adoptu") {
        target = "_blank"
        id = "nav-donate"
        span(classes = "material-symbols-outlined") { +Icons.DONATE }
        span { attributes["data-i18n"] = "donate"; +"Donate" }
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
            a("/rescuers") {
                span { attributes["data-i18n"] = "rescuerDirectory"; +"Rescuers" }
                span(classes = "material-symbols-outlined") { +Icons.GROUP }
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

// Browser-history back, not a fixed route - detail/edit pages are reached from several different
// listing/search contexts, and "wherever you actually came from" is the only destination that's
// always right. Wired by CommonModule.initBackLinks() (frontend/Common.kt), same delegated-click
// pattern as data-action - a plain history.back() has no server-renderable href.
fun MAIN.backLink() {
    a(href = "#", classes = "back-link") {
        span(classes = "material-symbols-outlined") { +Icons.ARROW_BACK }
        span { attributes["data-i18n"] = "back"; +"Back" }
    }
}

fun BODY.footer() {
    footer {
        a("/privacy") { attributes["data-i18n"] = "privacyPolicy"; +"Privacy Policy" }
        span { +" | " }
        a("/terms") { attributes["data-i18n"] = "termsConditions"; +"Terms and Conditions" }
        span { +" | " }
        // Hidden until the browser actually fires beforeinstallprompt (CommonModule.kt) - most
        // browsers don't (already installed, criteria not met, or no support at all e.g. iOS
        // Safari, which only offers Share -> Add to Home Screen with no programmatic prompt).
        a(href = "#", classes = "hidden") { id = "install-app-link"; attributes["data-i18n"] = "installApp"; +"Install App" }
        span(classes = "hidden") { id = "install-app-sep"; +" | " }
        span { +"© 2025 Adopt-U" }
        // Populated from GET /health's deploySequence field by CommonModule.initDeploySequence()
        // (frontend/Common.kt) - blank until that call resolves, so it never shows a stale "0".
        span(classes = "deploy-sequence") { id = "deploy-sequence" }
    }
}
