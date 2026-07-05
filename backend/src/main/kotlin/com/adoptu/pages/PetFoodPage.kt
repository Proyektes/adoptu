package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

data class FoodCategory(val type: String, val title: String, val items: List<FoodItem>)
data class FoodItem(val name: String, val detail: String, val description: String)

private val foodData: Map<String, List<FoodCategory>> = mapOf(
    "DOG" to listOf(
        FoodCategory("safe", "Safe Foods", listOf(
            FoodItem("Chicken", "Cooked, boneless", "Lean protein, easy to digest"),
            FoodItem("Rice", "White or brown", "Good source of carbohydrates"),
            FoodItem("Carrots", "Raw or cooked", "Low in calories, high in fiber"),
            FoodItem("Apples", "Without seeds", "Rich in vitamins"),
            FoodItem("Peanut Butter", "Unsalted, no xylitol", "Good protein source")
        )),
        FoodCategory("harmful", "Harmful Foods", listOf(
            FoodItem("Grapes", "All varieties", "Can cause kidney failure"),
            FoodItem("Onions", "All forms", "Damages red blood cells"),
            FoodItem("Chocolate", "All types", "Toxic to dogs"),
            FoodItem("Garlic", "All forms", "Causes anemia")
        )),
        FoodCategory("cannot", "Cannot Eat", listOf(
            FoodItem("Xylitol", "Artificial sweetener", "Highly toxic"),
            FoodItem("Macadamia Nuts", "All forms", "Causes weakness"),
            FoodItem("Avocado", "Pit, skin, all", "Contains persin"),
            FoodItem("Alcohol", "All forms", "Highly toxic")
        ))
    ),
    "CAT" to listOf(
        FoodCategory("safe", "Safe Foods", listOf(
            FoodItem("Cooked Fish", "Without bones", "High in protein"),
            FoodItem("Chicken", "Cooked, plain", "Good protein source"),
            FoodItem("Pumpkin", "Plain, cooked", "Helps digestion"),
            FoodItem("Eggs", "Cooked", "Complete protein")
        )),
        FoodCategory("harmful", "Harmful Foods", listOf(
            FoodItem("Raw Eggs", "With avidin", "Interferes with biotin"),
            FoodItem("Raw Fish", "Contains thiaminase", "Breaks down B vitamins"),
            FoodItem("Dog Food", "Any", "Lacks taurine"),
            FoodItem("Milk", "Most cats", "Lactose intolerance")
        )),
        FoodCategory("cannot", "Cannot Eat", listOf(
            FoodItem("Chocolate", "All types", "Theobromine toxic"),
            FoodItem("Onions/Garlic", "All forms", "Damages RBC"),
            FoodItem("Grapes/Raisins", "All varieties", "Kidney damage"),
            FoodItem("Alcohol", "All forms", "Very toxic")
        ))
    ),
    "BIRD" to listOf(
        FoodCategory("safe", "Safe Foods", listOf(
            FoodItem("Seeds", "Variety mix", "Good fat source"),
            FoodItem("Fruits", "Most varieties", "Vitamins and minerals"),
            FoodItem("Vegetables", "Leafy greens", "Essential nutrients"),
            FoodItem("Pellets", "Quality formulated", "Complete nutrition")
        )),
        FoodCategory("harmful", "Harmful Foods", listOf(
            FoodItem("Avocado", "All parts", "Persin toxic"),
            FoodItem("Fruit Pits", "Apple, peach", "Cyanide traces"),
            FoodItem("Salt", "Any form", "Toxic to birds"),
            FoodItem("Caffeine", "Coffee, tea", "Heart issues")
        )),
        FoodCategory("cannot", "Cannot Eat", listOf(
            FoodItem("Chocolate", "All types", "Theobromine toxic"),
            FoodItem("Onions", "All forms", "Damages RBC"),
            FoodItem("Garlic", "All forms", "Toxic"),
            FoodItem("Mushrooms", "Wild varieties", "Potential toxins")
        ))
    ),
    "FISH" to listOf(
        FoodCategory("safe", "Safe Foods", listOf(
            FoodItem("Flakes", "Quality brands", "Complete fish food"),
            FoodItem("Pellets", "Floating/sinking", "Balanced nutrition"),
            FoodItem("Frozen Food", "Bloodworms, brine", "Protein source"),
            FoodItem("Vegetables", "Blanched zucchini", "Fiber source")
        )),
        FoodCategory("harmful", "Harmful Foods", listOf(
            FoodItem("Bread", "Any type", "No nutrition, fills up"),
            FoodItem("Human Food", "Cooked meals", "Wrong nutrition"),
            FoodItem("Live Feed", "Wild caught insects", "Potential parasites")
        )),
        FoodCategory("cannot", "Cannot Eat", listOf(
            FoodItem("Land Insect", "Crawling bugs", "Not natural food"),
            FoodItem("Mammal Meat", "Chicken, beef", "Wrong for most fish"),
            FoodItem("Dairy", "Any", "Cannot digest"),
            FoodItem("Breadcrumbs", "Many contain garlic", "Harmful additives")
        ))
    ),
    "RABBIT" to listOf(
        FoodCategory("safe", "Safe Foods", listOf(
            FoodItem("Hay", "Timothy grass", "80% of diet"),
            FoodItem("Leafy Greens", "Romaine, cilantro", "Daily vitamins"),
            FoodItem("Pellets", "Timothy-based", "Balanced nutrition"),
            FoodItem("Carrots", "In moderation", "Treat")
        )),
        FoodCategory("harmful", "Harmful Foods", listOf(
            FoodItem("Iceberg Lettuce", "No nutrition", "Can cause GI stasis"),
            FoodItem("Beans", "All legumes", "Gas, digestive issues"),
            FoodItem("Corn", "Any form", "Cannot digest"),
            FoodItem("Potatoes", "Any form", "Wrong nutrients")
        )),
        FoodCategory("cannot", "Cannot Eat", listOf(
            FoodItem("Chocolate", "All types", "Theobromine toxic"),
            FoodItem("Onions", "All forms", "Very toxic"),
            FoodItem("Garlic", "All forms", "Toxic"),
            FoodItem("Avocado", "All parts", "Contains persin")
        ))
    )
)

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
                listOf("DOG" to "Dog", "CAT" to "Cat", "BIRD" to "Bird", "FISH" to "Fish", "RABBIT" to "Rabbit").forEach { (type, emoji) ->
                    button(classes = "pet-type-btn${if (type == "DOG") " active" else ""}", type = ButtonType.button) { 
                        attributes["data-type"] = type
                        +emoji
                    }
                }
            }
            
            h2 { 
                id = "selected-pet-type"
                attributes["data-i18n"] = "selectedPetInfo"
                +"Dog Food Information"
            }
            
            div { 
                id = "food-info"
                classes = setOf("food-grid") 
            }
            
            div(classes = "food-legend") {
                div(classes = "legend-item") {
                    span(classes = "legend-color safe"); +"Safe"
                }
                div(classes = "legend-item") {
                    span(classes = "legend-color harmful"); +"Harmful"
                }
                div(classes = "legend-item") {
                    span(classes = "legend-color toxic"); +"Toxic"
                }
            }
            
            p(classes = "vet-disclaimer") {
                attributes["data-i18n"] = "vetDisclaimer"
                +"This information is for reference only. Always consult your veterinarian before introducing new foods to your pet's diet."
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}