package com.adoptu.frontend.pages

import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

private data class FoodItem(val name: String, val detail: String, val description: String)
private data class FoodCategory(val type: String, val title: String, val items: List<FoodItem>)

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

@JsExport
@JsName("PetFoodPage")
object PetFoodPageModule {
    fun init() {
        document.querySelectorAll(".pet-type-btn").forEachElement { node ->
            val btn = node.unsafeCast<HTMLElement>()
            btn.addEventListener("click", {
                document.querySelectorAll(".pet-type-btn").forEachElement { b -> b.unsafeCast<HTMLElement>().classList.remove("active") }
                btn.classList.add("active")
                val type = btn.asDynamic().dataset.type.toString()
                showFoodInfo(type)
            })
        }
        showFoodInfo("DOG")
    }

    private fun showFoodInfo(petType: String) {
        val data = foodData[petType] ?: return
        val container = document.getElementById("food-info") ?: return
        val sb = StringBuilder()
        data.forEach { cat ->
            sb.append("<div class=\"food-category\"><h3>${cat.title}</h3><ul class=\"food-list\">")
            cat.items.forEach { item ->
                sb.append("<li><strong>${item.name}</strong>")
                if (item.detail.isNotEmpty()) sb.append(" <span class=\"food-detail\">(${item.detail})</span>")
                if (item.description.isNotEmpty()) sb.append("<p class=\"food-desc\">${item.description}</p>")
                sb.append("</li>")
            }
            sb.append("</ul></div>")
        }
        container.innerHTML = sb.toString()
    }
}
