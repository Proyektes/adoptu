package com.adoptu.site.pages

import kotlinx.html.*

// Public - site keys, unlike secret keys, are meant to be embedded in HTML. Matching server-side
// secret lives in infra/terraform.tfvars's turnstile_secret_key (gitignored).
private const val TURNSTILE_SITE_KEY = "0x4AAAAAAEqpqMlvXIJr_ApK"

fun HTML.reportLostFoundPage(navParams: NavParams = NavParams()) {
    commonHead("Report a Lost or Found Pet - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "card-bg profile-section") {
                h1 { attributes["data-i18n"] = "reportLostFoundTitle"; +"Report a Lost or Found Pet" }
                p { attributes["data-i18n"] = "reportLostFoundIntro"; +"Lost your pet, or found a stray? File a report and we'll check it against other reports nearby." }
                p { a("/lost-found") { attributes["data-i18n"] = "browseLostFound"; +"Browse existing lost & found reports" } }

                div(classes = "form-row") {
                    div(classes = "kind-toggle") {
                        button(classes = "kind-btn active", type = ButtonType.button) {
                            id = "kind-lost"; attributes["data-kind"] = "LOST"
                            span { attributes["data-i18n"] = "kindLost"; +"I lost my pet" }
                        }
                        button(classes = "kind-btn", type = ButtonType.button) {
                            id = "kind-found"; attributes["data-kind"] = "FOUND"
                            span { attributes["data-i18n"] = "kindFound"; +"I found a pet" }
                        }
                    }
                }

                div(classes = "form-row") {
                    label { htmlFor = "pet-type"; attributes["data-i18n"] = "petType"; +"Type of pet" }
                    select {
                        id = "pet-type"
                        option { value = ""; attributes["data-i18n"] = "petTypeUnknown"; +"Not sure" }
                        option { value = "DOG"; attributes["data-i18n"] = "dog"; +"Dog" }
                        option { value = "CAT"; attributes["data-i18n"] = "cat"; +"Cat" }
                        option { value = "BIRD"; attributes["data-i18n"] = "bird"; +"Bird" }
                        option { value = "FISH"; attributes["data-i18n"] = "fish"; +"Fish" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "description"; attributes["data-i18n"] = "description"; +"Description" }
                    textArea { id = "description"; required = true; rows = "4" }
                }

                div(classes = "form-row") {
                    label { attributes["data-i18n"] = "location"; +"Location" }
                    button(classes = "btn btn-secondary", type = ButtonType.button) {
                        id = "use-my-location-btn"
                        attributes["data-i18n"] = "useMyLocation"
                        +"Use my current location"
                    }
                    p(classes = "field-error") { id = "location-status" }
                }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "report-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                        select { id = "report-country"; countrySelect("report-country", true) }
                    }
                    div {
                        label { htmlFor = "report-city"; attributes["data-i18n"] = "city"; +"City" }
                        input(InputType.text) { id = "report-city" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "report-state"; attributes["data-i18n"] = "state"; +"State (optional)" }
                    input(InputType.text) { id = "report-state" }
                }

                div(classes = "form-row") {
                    id = "reporter-contact-row"
                    label { htmlFor = "reporter-email"; attributes["data-i18n"] = "email"; +"Your email (so we can follow up)" }
                    input(InputType.email) { id = "reporter-email" }
                }
                div(classes = "form-row") {
                    id = "reporter-phone-row"
                    label { htmlFor = "reporter-phone"; attributes["data-i18n"] = "phoneOptional"; +"Your phone (optional)" }
                    input(InputType.tel) { id = "reporter-phone" }
                }

                div(classes = "form-row hidden") {
                    id = "captcha-row"
                    div {
                        id = "turnstile-widget"
                        classes = setOf("cf-turnstile")
                        attributes["data-sitekey"] = TURNSTILE_SITE_KEY
                    }
                }

                p { id = "message"; +"" }
                button(classes = "btn", type = ButtonType.button) { id = "submit-btn"; attributes["data-i18n"] = "submitReport"; +"Submit Report" }
            }
        }
        footer()
        script(src = "https://challenges.cloudflare.com/turnstile/v0/api.js") { attributes["async"] = ""; attributes["defer"] = "" }
        commonScripts()
    }
}

fun HTML.lostFoundBrowsePage(navParams: NavParams = NavParams()) {
    commonHead("Lost & Found Pets - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "lostFoundBrowseTitle"; +"Lost & Found Pets" }
            a("/report-lost-found", classes = "btn") { attributes["data-i18n"] = "reportLostFoundTitle"; +"Report a Lost or Found Pet" }
            div(classes = "form-row") {
                label { htmlFor = "browse-kind"; attributes["data-i18n"] = "showing"; +"Showing" }
                select {
                    id = "browse-kind"
                    option { value = "LOST"; attributes["data-i18n"] = "kindLostPlural"; +"Lost pets" }
                    option { value = "FOUND"; attributes["data-i18n"] = "kindFoundPlural"; +"Found pets" }
                }
            }
            div(classes = "location-search-form") {
                locationSearchFilters(includeNeighborhood = false)
                button(classes = "btn", type = ButtonType.button) { id = "search-btn"; attributes["data-i18n"] = "search"; +"Search" }
            }
            div { id = "lost-found-error"; classes = setOf("error-message", "hidden") }
            div { id = "lost-found-results"; classes = setOf("pet-grid"); +"" }
        }
        footer()
        commonScripts()
    }
}

// Single generic shell for any /lost-found/{id} - same reasoning as pet-detail.html (SiteGenerator
// writes one flat file; the id is read from window.location.pathname client-side).
fun HTML.lostFoundDetailPage(navParams: NavParams = NavParams()) {
    commonHead("Lost & Found Report - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div { id = "lost-found-detail"; classes = setOf("pet-detail"); +"" }
            div { id = "message"; +"" }
        }
        footer()
        commonScripts()
    }
}

fun HTML.lostFoundResolvePage(navParams: NavParams = NavParams()) {
    commonHead("Resolve Lost & Found Report - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "verification-container") {
                div(classes = "verification-success hidden") {
                    id = "resolve-success"
                    h1(classes = "success-title") { attributes["data-i18n"] = "reportResolvedTitle"; +"Report Resolved" }
                    div(classes = "success-icon") {
                        unsafe { raw("""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="green" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>""") }
                    }
                    p(classes = "success-message") { attributes["data-i18n"] = "reportResolvedMessage"; +"Thanks for the update - this report is now closed." }
                }
                div(classes = "verification-error hidden") {
                    id = "resolve-error"
                    h1(classes = "error-title") { attributes["data-i18n"] = "reportResolveFailedTitle"; +"Couldn't Resolve" }
                    div(classes = "error-icon") {
                        unsafe { raw("""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="red" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>""") }
                    }
                    p(classes = "error-message") { attributes["data-i18n"] = "reportResolveFailedMessage"; +"This report was already resolved, or the link is invalid or expired." }
                }
            }
        }
        footer()
        commonScripts()
    }
}
