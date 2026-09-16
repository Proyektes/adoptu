package com.adoptu.site.pages

import kotlinx.html.*

fun HTML.myPetsPage(navParams: NavParams = NavParams()) {
    commonHead("My Pets - Adopt-U", "mypets.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "myPets"; +"My Pets" }
            div { id = "medical-events-overview-section"; classes = setOf("mb-2rem", "hidden")
                h2 { attributes["data-i18n"] = "medicalEventsOverview"; +"Medical Events Overview" }
                div { id = "medical-events-overview"; +"" }
            }
            div { id = "adoption-requests-section"; classes = setOf("mb-2rem")
                h2 { attributes["data-i18n"] = "adoptionRequests"; +"Adoption Requests" }
                div { id = "adoption-requests"; +"" }
            }
            div { id = "volunteer-applications-section"; classes = setOf("mb-2rem")
                h2 { attributes["data-i18n"] = "volunteerApplications"; +"Volunteer Applications" }
                div { id = "volunteer-applications"; +"" }
            }
            div { id = "pet-edit-suggestions-section"; classes = setOf("mb-2rem")
                h2 { attributes["data-i18n"] = "petEditSuggestions"; +"Pet Edit Suggestions" }
                div { id = "pet-edit-suggestions"; +"" }
            }
            div { id = "sponsorship-offers-section"; classes = setOf("mb-2rem")
                h2 { attributes["data-i18n"] = "sponsorshipOffersReceived"; +"Sponsorship Offers" }
                div { id = "sponsorship-offers"; +"" }
            }
            a(href = "/edit-pet", classes = "btn") { attributes["data-i18n"] = "addPet"; +"Add Pet" }
            div { id = "pets"; classes = setOf("pet-grid", "mt-2rem"); +"" }
        }
        footer()
        commonScripts()
    }
}
