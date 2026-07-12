package com.adoptu.frontend.pages

import com.adoptu.frontend.I18n
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

private data class FoodItemKey(val nameKey: String, val detailKey: String, val descKey: String)
private data class FoodCategoryKey(val titleKey: String, val items: List<FoodItemKey>)

private val foodDataKeys: Map<String, List<FoodCategoryKey>> = mapOf(
    "DOG" to listOf(
        FoodCategoryKey("safeFoods", listOf(
            FoodItemKey("food_dog_safe_chicken_name", "food_dog_safe_chicken_detail", "food_dog_safe_chicken_desc"),
            FoodItemKey("food_dog_safe_rice_name", "food_dog_safe_rice_detail", "food_dog_safe_rice_desc"),
            FoodItemKey("food_dog_safe_carrots_name", "food_dog_safe_carrots_detail", "food_dog_safe_carrots_desc"),
            FoodItemKey("food_dog_safe_apples_name", "food_dog_safe_apples_detail", "food_dog_safe_apples_desc"),
            FoodItemKey("food_dog_safe_peanutbutter_name", "food_dog_safe_peanutbutter_detail", "food_dog_safe_peanutbutter_desc")
        )),
        FoodCategoryKey("harmfulFoods", listOf(
            FoodItemKey("food_dog_harmful_grapes_name", "food_dog_harmful_grapes_detail", "food_dog_harmful_grapes_desc"),
            FoodItemKey("food_dog_harmful_onions_name", "food_dog_harmful_onions_detail", "food_dog_harmful_onions_desc"),
            FoodItemKey("food_dog_harmful_chocolate_name", "food_dog_harmful_chocolate_detail", "food_dog_harmful_chocolate_desc"),
            FoodItemKey("food_dog_harmful_garlic_name", "food_dog_harmful_garlic_detail", "food_dog_harmful_garlic_desc")
        )),
        FoodCategoryKey("cannotEatFoods", listOf(
            FoodItemKey("food_dog_cannot_xylitol_name", "food_dog_cannot_xylitol_detail", "food_dog_cannot_xylitol_desc"),
            FoodItemKey("food_dog_cannot_macadamia_name", "food_dog_cannot_macadamia_detail", "food_dog_cannot_macadamia_desc"),
            FoodItemKey("food_dog_cannot_avocado_name", "food_dog_cannot_avocado_detail", "food_dog_cannot_avocado_desc"),
            FoodItemKey("food_dog_cannot_alcohol_name", "food_dog_cannot_alcohol_detail", "food_dog_cannot_alcohol_desc")
        ))
    ),
    "CAT" to listOf(
        FoodCategoryKey("safeFoods", listOf(
            FoodItemKey("food_cat_safe_cookedfish_name", "food_cat_safe_cookedfish_detail", "food_cat_safe_cookedfish_desc"),
            FoodItemKey("food_cat_safe_chicken_name", "food_cat_safe_chicken_detail", "food_cat_safe_chicken_desc"),
            FoodItemKey("food_cat_safe_pumpkin_name", "food_cat_safe_pumpkin_detail", "food_cat_safe_pumpkin_desc"),
            FoodItemKey("food_cat_safe_eggs_name", "food_cat_safe_eggs_detail", "food_cat_safe_eggs_desc")
        )),
        FoodCategoryKey("harmfulFoods", listOf(
            FoodItemKey("food_cat_harmful_raweggs_name", "food_cat_harmful_raweggs_detail", "food_cat_harmful_raweggs_desc"),
            FoodItemKey("food_cat_harmful_rawfish_name", "food_cat_harmful_rawfish_detail", "food_cat_harmful_rawfish_desc"),
            FoodItemKey("food_cat_harmful_dogfood_name", "food_cat_harmful_dogfood_detail", "food_cat_harmful_dogfood_desc"),
            FoodItemKey("food_cat_harmful_milk_name", "food_cat_harmful_milk_detail", "food_cat_harmful_milk_desc")
        )),
        FoodCategoryKey("cannotEatFoods", listOf(
            FoodItemKey("food_cat_cannot_chocolate_name", "food_cat_cannot_chocolate_detail", "food_cat_cannot_chocolate_desc"),
            FoodItemKey("food_cat_cannot_onionsgarlic_name", "food_cat_cannot_onionsgarlic_detail", "food_cat_cannot_onionsgarlic_desc"),
            FoodItemKey("food_cat_cannot_grapesraisins_name", "food_cat_cannot_grapesraisins_detail", "food_cat_cannot_grapesraisins_desc"),
            FoodItemKey("food_cat_cannot_alcohol_name", "food_cat_cannot_alcohol_detail", "food_cat_cannot_alcohol_desc")
        ))
    ),
    "BIRD" to listOf(
        FoodCategoryKey("safeFoods", listOf(
            FoodItemKey("food_bird_safe_seeds_name", "food_bird_safe_seeds_detail", "food_bird_safe_seeds_desc"),
            FoodItemKey("food_bird_safe_fruits_name", "food_bird_safe_fruits_detail", "food_bird_safe_fruits_desc"),
            FoodItemKey("food_bird_safe_vegetables_name", "food_bird_safe_vegetables_detail", "food_bird_safe_vegetables_desc"),
            FoodItemKey("food_bird_safe_pellets_name", "food_bird_safe_pellets_detail", "food_bird_safe_pellets_desc")
        )),
        FoodCategoryKey("harmfulFoods", listOf(
            FoodItemKey("food_bird_harmful_avocado_name", "food_bird_harmful_avocado_detail", "food_bird_harmful_avocado_desc"),
            FoodItemKey("food_bird_harmful_fruitpits_name", "food_bird_harmful_fruitpits_detail", "food_bird_harmful_fruitpits_desc"),
            FoodItemKey("food_bird_harmful_salt_name", "food_bird_harmful_salt_detail", "food_bird_harmful_salt_desc"),
            FoodItemKey("food_bird_harmful_caffeine_name", "food_bird_harmful_caffeine_detail", "food_bird_harmful_caffeine_desc")
        )),
        FoodCategoryKey("cannotEatFoods", listOf(
            FoodItemKey("food_bird_cannot_chocolate_name", "food_bird_cannot_chocolate_detail", "food_bird_cannot_chocolate_desc"),
            FoodItemKey("food_bird_cannot_onions_name", "food_bird_cannot_onions_detail", "food_bird_cannot_onions_desc"),
            FoodItemKey("food_bird_cannot_garlic_name", "food_bird_cannot_garlic_detail", "food_bird_cannot_garlic_desc"),
            FoodItemKey("food_bird_cannot_mushrooms_name", "food_bird_cannot_mushrooms_detail", "food_bird_cannot_mushrooms_desc")
        ))
    ),
    "FISH" to listOf(
        FoodCategoryKey("safeFoods", listOf(
            FoodItemKey("food_fish_safe_flakes_name", "food_fish_safe_flakes_detail", "food_fish_safe_flakes_desc"),
            FoodItemKey("food_fish_safe_pellets_name", "food_fish_safe_pellets_detail", "food_fish_safe_pellets_desc"),
            FoodItemKey("food_fish_safe_frozenfood_name", "food_fish_safe_frozenfood_detail", "food_fish_safe_frozenfood_desc"),
            FoodItemKey("food_fish_safe_vegetables_name", "food_fish_safe_vegetables_detail", "food_fish_safe_vegetables_desc")
        )),
        FoodCategoryKey("harmfulFoods", listOf(
            FoodItemKey("food_fish_harmful_bread_name", "food_fish_harmful_bread_detail", "food_fish_harmful_bread_desc"),
            FoodItemKey("food_fish_harmful_humanfood_name", "food_fish_harmful_humanfood_detail", "food_fish_harmful_humanfood_desc"),
            FoodItemKey("food_fish_harmful_livefeed_name", "food_fish_harmful_livefeed_detail", "food_fish_harmful_livefeed_desc")
        )),
        FoodCategoryKey("cannotEatFoods", listOf(
            FoodItemKey("food_fish_cannot_landinsect_name", "food_fish_cannot_landinsect_detail", "food_fish_cannot_landinsect_desc"),
            FoodItemKey("food_fish_cannot_mammalmeat_name", "food_fish_cannot_mammalmeat_detail", "food_fish_cannot_mammalmeat_desc"),
            FoodItemKey("food_fish_cannot_dairy_name", "food_fish_cannot_dairy_detail", "food_fish_cannot_dairy_desc"),
            FoodItemKey("food_fish_cannot_breadcrumbs_name", "food_fish_cannot_breadcrumbs_detail", "food_fish_cannot_breadcrumbs_desc")
        ))
    ),
    "RABBIT" to listOf(
        FoodCategoryKey("safeFoods", listOf(
            FoodItemKey("food_rabbit_safe_hay_name", "food_rabbit_safe_hay_detail", "food_rabbit_safe_hay_desc"),
            FoodItemKey("food_rabbit_safe_leafygreens_name", "food_rabbit_safe_leafygreens_detail", "food_rabbit_safe_leafygreens_desc"),
            FoodItemKey("food_rabbit_safe_pellets_name", "food_rabbit_safe_pellets_detail", "food_rabbit_safe_pellets_desc"),
            FoodItemKey("food_rabbit_safe_carrots_name", "food_rabbit_safe_carrots_detail", "food_rabbit_safe_carrots_desc")
        )),
        FoodCategoryKey("harmfulFoods", listOf(
            FoodItemKey("food_rabbit_harmful_icebergletttuce_name", "food_rabbit_harmful_icebergletttuce_detail", "food_rabbit_harmful_icebergletttuce_desc"),
            FoodItemKey("food_rabbit_harmful_beans_name", "food_rabbit_harmful_beans_detail", "food_rabbit_harmful_beans_desc"),
            FoodItemKey("food_rabbit_harmful_corn_name", "food_rabbit_harmful_corn_detail", "food_rabbit_harmful_corn_desc"),
            FoodItemKey("food_rabbit_harmful_potatoes_name", "food_rabbit_harmful_potatoes_detail", "food_rabbit_harmful_potatoes_desc")
        )),
        FoodCategoryKey("cannotEatFoods", listOf(
            FoodItemKey("food_rabbit_cannot_chocolate_name", "food_rabbit_cannot_chocolate_detail", "food_rabbit_cannot_chocolate_desc"),
            FoodItemKey("food_rabbit_cannot_onions_name", "food_rabbit_cannot_onions_detail", "food_rabbit_cannot_onions_desc"),
            FoodItemKey("food_rabbit_cannot_garlic_name", "food_rabbit_cannot_garlic_detail", "food_rabbit_cannot_garlic_desc"),
            FoodItemKey("food_rabbit_cannot_avocado_name", "food_rabbit_cannot_avocado_detail", "food_rabbit_cannot_avocado_desc")
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
                document.getElementById("selected-pet-type")?.textContent = "${I18n.t(type.lowercase())} ${I18n.t("foodInformation")}"
                showFoodInfo(type)
            })
        }
        showFoodInfo("DOG")
    }

    private fun showFoodInfo(petType: String) {
        val data = foodDataKeys[petType] ?: return
        val container = document.getElementById("food-info") ?: return
        val sb = StringBuilder()
        data.forEach { cat ->
            sb.append("<div class=\"food-category\"><h3>${I18n.t(cat.titleKey)}</h3><ul class=\"food-list\">")
            cat.items.forEach { item ->
                sb.append("<li><strong>${I18n.t(item.nameKey)}</strong>")
                val detail = I18n.t(item.detailKey)
                if (detail.isNotEmpty()) sb.append(" <span class=\"food-detail\">($detail)</span>")
                val desc = I18n.t(item.descKey)
                if (desc.isNotEmpty()) sb.append("<p class=\"food-desc\">$desc</p>")
                sb.append("</li>")
            }
            sb.append("</ul></div>")
        }
        container.innerHTML = sb.toString()
    }
}
