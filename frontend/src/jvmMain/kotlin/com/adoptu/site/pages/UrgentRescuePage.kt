package com.adoptu.site.pages

import kotlinx.html.*

// Public - site keys, unlike secret keys, are meant to be embedded in HTML. Matching server-side
// secret lives in infra/terraform.tfvars's turnstile_secret_key (gitignored).
private const val TURNSTILE_SITE_KEY = "0x4AAAAAAEqpqMlvXIJr_ApK"

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
                p(classes = "field-hint") { attributes["data-i18n"] = "concreteAddressHint"; +"Add a street and number if you can - this is what lets a rescuer take a taxi or open a maps app straight to the spot." }
                div(classes = "form-row-two-col") {
                    div {
                        label { htmlFor = "report-street"; attributes["data-i18n"] = "street"; +"Street" }
                        input(InputType.text) { id = "report-street" }
                    }
                    div {
                        label { htmlFor = "report-exterior-number"; attributes["data-i18n"] = "exteriorNumber"; +"Exterior number" }
                        input(InputType.text) { id = "report-exterior-number" }
                    }
                }
                div(classes = "form-row") {
                    label { htmlFor = "report-reference-notes"; attributes["data-i18n"] = "referenceNotes"; +"Reference notes (e.g. house color, nearby landmark)" }
                    input(InputType.text) { id = "report-reference-notes" }
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

// Landing page for the accept link sent by email/SMS (see UrgentRescueService.matchAndPage) - works
// unauthenticated (tapped from a phone), reads ?token= client-side and calls
// GET /api/urgent-reports/accept, same shell pattern as EmailVerificationPage.
fun HTML.urgentRescueAcceptPage(navParams: NavParams = NavParams()) {
    commonHead("Accept Urgent Rescue - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "verification-container") {
                div(classes = "verification-success hidden") {
                    id = "accept-success"
                    h1(classes = "success-title") { attributes["data-i18n"] = "rescueAcceptedTitle"; +"Rescue Accepted" }
                    div(classes = "success-icon") {
                        unsafe { raw("""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="green" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>""") }
                    }
                    p(classes = "success-message") { attributes["data-i18n"] = "rescueAcceptedMessage"; +"Thank you - you're on it. Head to the reported location as soon as you can." }
                }
                div(classes = "verification-error hidden") {
                    id = "accept-error"
                    h1(classes = "error-title") { attributes["data-i18n"] = "rescueAcceptFailedTitle"; +"Couldn't Accept" }
                    div(classes = "error-icon") {
                        unsafe { raw("""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="red" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>""") }
                    }
                    p(classes = "error-message") { id = "accept-error-message"; attributes["data-i18n"] = "rescueAcceptFailedMessage"; +"This report was already accepted by another rescuer, or the link is invalid or expired." }
                }
            }
        }
        footer()
        commonScripts()
    }
}
