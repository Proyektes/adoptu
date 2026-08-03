package com.adoptu.site.pages

import kotlinx.html.*

fun HTML.petFoodPage(navParams: NavParams = NavParams()) {
    commonHead("Pet Food Guide - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "petFoodGuide"; +"Pet Food Guide" }
            p { attributes["data-i18n"] = "petFoodDescription"; +"Learn which foods are safe, harmful, or toxic for your pets" }
            
            div(classes = "pet-type-selector") {
                listOf("DOG" to "dog", "CAT" to "cat", "BIRD" to "bird", "FISH" to "fish", "RABBIT" to "rabbit").forEach { (type, i18nKey) ->
                    button(classes = "pet-type-btn${if (type == "DOG") " active" else ""}", type = ButtonType.button) {
                        attributes["data-type"] = type
                        attributes["data-i18n"] = i18nKey
                        +i18nKey
                    }
                }
            }

            h2 {
                id = "selected-pet-type"
                span { attributes["data-i18n"] = "dog"; +"Dog" }
                +" "
                span { attributes["data-i18n"] = "foodInformation"; +"Food Information" }
            }

            div {
                id = "food-info"
                classes = setOf("food-grid")
            }

            div(classes = "food-legend") {
                div(classes = "legend-item") {
                    span(classes = "legend-color safe"); span { attributes["data-i18n"] = "legendSafe"; +"Safe" }
                }
                div(classes = "legend-item") {
                    span(classes = "legend-color harmful"); span { attributes["data-i18n"] = "legendHarmful"; +"Harmful" }
                }
                div(classes = "legend-item") {
                    span(classes = "legend-color toxic"); span { attributes["data-i18n"] = "legendToxic"; +"Toxic" }
                }
            }
            
            p(classes = "vet-disclaimer") {
                attributes["data-i18n"] = "vetDisclaimer"
                +"This information is for reference only. Always consult your veterinarian before introducing new foods to your pet's diet."
            }
        }
        footer()
        commonScripts()
    }
}