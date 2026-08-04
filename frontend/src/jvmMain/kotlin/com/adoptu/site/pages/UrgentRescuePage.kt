package com.adoptu.site.pages

import kotlinx.html.*

// Cloudflare's published always-pass test site key (public - site keys, unlike secret keys, are
// meant to be embedded in HTML). Swap for the real Turnstile site key before relying on this as
// actual spam protection - see infra/variables.tf's turnstile_secret_key for the matching
// server-side secret and https://developers.cloudflare.com/turnstile/troubleshooting/testing/.
private const val TURNSTILE_TEST_SITE_KEY = "1x00000000000000000000AA"

// Public - works for both anonymous visitors and logged-in users (ReportUrgentPageModule decides
// which fields to show based on GET /api/auth/me, same pattern as CommonModule.initAuthNav()).
fun HTML.reportUrgentPage(navParams: NavParams = NavParams()) {
    commonHead("Report a Pet in Danger - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "card-bg profile-section") {
                h1 { attributes["data-i18n"] = "reportUrgentTitle"; +"Report a Pet in Danger" }
                p { attributes["data-i18n"] = "reportUrgentIntro"; +"If you've found an injured, abused, starving, or abandoned pet that needs immediate help, tell us where and what's wrong - we'll alert nearby urgent rescuers right away." }

                div(classes = "form-row") {
                    label { htmlFor = "danger-type"; attributes["data-i18n"] = "dangerType"; +"What's wrong?" }
                    select {
                        id = "danger-type"
                        option { value = "INJURED"; attributes["data-i18n"] = "dangerInjured"; +"Injured" }
                        option { value = "ABUSED"; attributes["data-i18n"] = "dangerAbused"; +"Being abused / mistreated" }
                        option { value = "STARVING"; attributes["data-i18n"] = "dangerStarving"; +"Starving" }
                        option { value = "TOO_YOUNG"; attributes["data-i18n"] = "dangerTooYoung"; +"Too young to survive alone" }
                        option { value = "OTHER"; attributes["data-i18n"] = "dangerOther"; +"Other emergency" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "description"; attributes["data-i18n"] = "description"; +"Describe the situation" }
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
                        attributes["data-sitekey"] = TURNSTILE_TEST_SITE_KEY
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

fun HTML.urgentRescuerProfilePage(navParams: NavParams = NavParams()) {
    commonHead("Urgent Rescuer Settings - Adopt-U")
    body {
        // Any logged-in user can reach this page to opt in (fill in phone/coverage area and
        // activate) - not gated to users who are already URGENT_RESCUER, unlike the dashboard
        // below. Matches how /profile's role checkboxes work for every other role.
        attributes["data-auth-required"] = "user"
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "card-bg profile-section") {
                h1 { attributes["data-i18n"] = "urgentRescuerSettingsTitle"; +"Urgent Rescuer Settings" }
                p { attributes["data-i18n"] = "urgentRescuerSettingsIntro"; +"Get paged when a pet in danger is reported near your coverage area. First to accept takes the case." }

                div(classes = "form-row") {
                    div(classes = "checkbox-row") {
                        input(InputType.checkBox) { id = "urgent-rescuer-active" }
                        span { attributes["data-i18n"] = "urgentRescuerActive"; +"I'm available to respond to urgent reports" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "urgent-phone"; attributes["data-i18n"] = "phone"; +"Phone (for SMS alerts)" }
                    input(InputType.tel) { id = "urgent-phone"; required = true }
                }

                div(classes = "form-row") {
                    label { attributes["data-i18n"] = "coverageAreaMode"; +"Coverage area" }
                    div(classes = "checkbox-row") {
                        input(InputType.radio) { id = "mode-coordinates"; name = "input-mode"; checked = true }
                        span { attributes["data-i18n"] = "useMyLocationAndRadius"; +"Use my location + a radius" }
                    }
                    div(classes = "checkbox-row") {
                        input(InputType.radio) { id = "mode-zone"; name = "input-mode" }
                        span { attributes["data-i18n"] = "pickAZone"; +"Pick a country/state/city instead" }
                    }
                }

                div { id = "coordinates-fields"
                    div(classes = "form-row") {
                        button(classes = "btn btn-secondary", type = ButtonType.button) {
                            id = "capture-location-btn"
                            attributes["data-i18n"] = "useMyLocation"
                            +"Use my current location"
                        }
                        p(classes = "field-error") { id = "coordinates-status" }
                    }
                    div(classes = "form-row") {
                        label { htmlFor = "radius-km"; attributes["data-i18n"] = "radiusKm"; +"Radius (km)" }
                        input(InputType.number) { id = "radius-km"; value = "10"; min = "1"; max = "200" }
                    }
                }
                div(classes = "form-row-two-col hidden") { id = "zone-fields"
                    div {
                        label { htmlFor = "urgent-zone-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
                        select { id = "urgent-zone-country"; countrySelect("urgent-zone-country", true) }
                    }
                    div {
                        label { htmlFor = "urgent-zone-city"; attributes["data-i18n"] = "city"; +"City" }
                        input(InputType.text) { id = "urgent-zone-city" }
                    }
                }

                p { id = "message"; +"" }
                button(classes = "btn", type = ButtonType.button) { id = "save-urgent-profile-btn"; attributes["data-i18n"] = "save"; +"Save" }
            }
        }
        footer()
        commonScripts()
    }
}

fun HTML.urgentRescuerDashboardPage(navParams: NavParams = NavParams()) {
    commonHead("Urgent Rescue Alerts - Adopt-U")
    body {
        attributes["data-auth-required"] = "urgent-rescuer"
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "urgentRescueAlertsTitle"; +"Urgent Rescue Alerts" }
            div { id = "pages-empty"; classes = setOf("pets-empty-state") }
            div { id = "pages-container"; classes = setOf("pet-grid"); +"" }
        }
        footer()
        commonScripts()
    }
}

fun HTML.urgentRescuerLeaderboardPage(navParams: NavParams = NavParams()) {
    commonHead("Urgent Rescuer Leaderboard - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "urgentRescuerLeaderboardTitle"; +"Top Urgent Rescuers (last 12 months)" }
            div { id = "leaderboard-container"; +"" }
        }
        footer()
        commonScripts()
    }
}
