package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import kotlin.js.Promise

private val emoji = mapOf("DOG" to "🐕", "CAT" to "🐱", "BIRD" to "🐦", "FISH" to "🐟")

private const val PAGE_SIZE = 12
// Distance (in px) from the sentinel at which the next batch is rendered, so it's
// ready before the user actually scrolls to the bottom of the currently visible pets.
private const val PRELOAD_MARGIN = "600px 0px"

private const val PAW_ICON = "<svg class=\"pets-empty-icon\" viewBox=\"0 0 100 100\" width=\"88\" height=\"88\" aria-hidden=\"true\">" +
    "<circle cx=\"50\" cy=\"62\" r=\"17\" fill=\"currentColor\" opacity=\"0.3\"/>" +
    "<ellipse cx=\"26\" cy=\"38\" rx=\"9\" ry=\"12\" fill=\"currentColor\" opacity=\"0.3\"/>" +
    "<ellipse cx=\"44\" cy=\"24\" rx=\"9\" ry=\"12\" fill=\"currentColor\" opacity=\"0.3\"/>" +
    "<ellipse cx=\"64\" cy=\"24\" rx=\"9\" ry=\"12\" fill=\"currentColor\" opacity=\"0.3\"/>" +
    "<ellipse cx=\"80\" cy=\"40\" rx=\"9\" ry=\"12\" fill=\"currentColor\" opacity=\"0.3\"/>" +
    "</svg>"

@JsExport
@JsName("IndexPage")
object IndexPageModule {
    private var currentType = ""
    private var currentSex = ""
    private var allPets: Array<dynamic> = arrayOf()
    private var renderedCount = 0
    private var loadRequestId = 0
    private var scrollObserver: dynamic = null

    fun init() {
        val countrySelect = document.getElementById("pets-country") as? HTMLSelectElement

        document.querySelectorAll(".filter-btn").forEachElement { node ->
            val btn = node.unsafeCast<HTMLElement>()
            btn.addEventListener("click", {
                document.querySelectorAll(".filter-btn").forEachElement { b -> b.unsafeCast<HTMLElement>().classList.remove("active") }
                btn.classList.add("active")
                currentType = btn.asDynamic().dataset.type?.toString() ?: ""
                loadPets()
            })
        }

        val sexFilter = document.querySelector(".filter-sex") as? HTMLSelectElement
        sexFilter?.addEventListener("change", {
            currentSex = sexFilter.value
            loadPets()
        })

        countrySelect?.addEventListener("change", { loadPets() })

        ApiClientModule.me().then<Unit> { user ->
            if (countrySelect != null && user.authenticated != false && user.country != null) {
                countrySelect.value = user.country.toString()
            }
        }.catch { }.then<Unit> { loadPets() }
    }

    fun loadPets(): Promise<Unit> {
        val countrySelect = document.getElementById("pets-country") as? HTMLSelectElement
        val country = countrySelect?.value ?: ""
        updateCountryHint()
        val requestId = ++loadRequestId

        scrollObserver?.disconnect()
        scrollObserver = null

        val errorDiv = document.getElementById("pets-error").unsafeCast<HTMLElement?>()
        errorDiv?.style?.display = "none"
        errorDiv?.textContent = ""

        val container = document.getElementById("pets").unsafeCast<HTMLElement?>()
        container?.innerHTML = ""
        allPets = arrayOf()
        renderedCount = 0
        document.getElementById("pets-sentinel")?.textContent = ""

        if (country.isEmpty()) {
            showEmptyState("chooseCountryPrompt", "chooseCountryPromptHint")
            return Promise.resolve(Unit)
        }
        hideEmptyState()

        return ApiClientModule.getPets(currentType.ifEmpty { null }, country).then<Unit> { pets ->
            if (requestId != loadRequestId) return@then // a newer filter change superseded this request

            val list = (pets as? Array<dynamic>) ?: arrayOf()
            allPets = list.filter { p -> currentSex.isEmpty() || p.sex == currentSex }.toTypedArray()
            if (allPets.isEmpty()) {
                showEmptyState("noPetsFound", "noPetsFoundHint")
                return@then
            }
            renderNextBatch()
            setupInfiniteScroll()
        }
    }

    private fun updateCountryHint() {
        val countrySelect = document.getElementById("pets-country") as? HTMLSelectElement
        val hint = document.getElementById("pets-country-hint").unsafeCast<HTMLElement?>()
        val filtersDiv = document.getElementById("pets-filters").unsafeCast<HTMLElement?>()
        val hasCountry = countrySelect?.value?.isNotEmpty() == true
        hint?.style?.display = if (hasCountry) "none" else ""
        filtersDiv?.style?.display = if (hasCountry) "" else "none"
    }

    private fun showEmptyState(titleKey: String, hintKey: String) {
        val emptyDiv = document.getElementById("pets-empty").unsafeCast<HTMLElement?>() ?: return
        emptyDiv.innerHTML = "$PAW_ICON<h3>${I18n.t(titleKey)}</h3><p>${I18n.t(hintKey)}</p>"
        emptyDiv.style.display = "flex"
    }

    private fun hideEmptyState() {
        document.getElementById("pets-empty").unsafeCast<HTMLElement?>()?.style?.display = "none"
    }

    private fun petCardHtml(p: dynamic): String {
        val images = p.images as? Array<dynamic>
        val primaryImage = images?.firstOrNull { it.isPrimary == true } ?: images?.firstOrNull()
        val imageHtml = if (primaryImage != null) {
            "<img src=\"${primaryImage.imageUrl}\" alt=\"${p.name}\" loading=\"lazy\">"
        } else {
            "<div class=\"pet-card-placeholder\">${emoji[p.type.toString()] ?: "🐾"}</div>"
        }
        val sexClass = if (p.sex == "MALE") "male" else "female"
        val sizeHtml = if (p.size != null) "<span class=\"pet-size\">${p.size}</span>" else ""
        val urgent = if (p.isUrgent == true) " ⚠️" else ""
        val breedHtml = if (p.breed != null) "<span class=\"pet-breed\">${p.breed}</span>" else ""
        val rescueDateHtml = if (p.rescueDate != null) {
            val date = js("new Date(p.rescueDate)").toLocaleDateString()
            "<span class=\"label\">${I18n.t("rescued")}</span><span class=\"value\">$date</span>"
        } else ""
        return "<a href=\"/pet/${p.id}\" class=\"pet-card\">$imageHtml<div class=\"pet-card-body\">" +
            "<span class=\"pet-type\">${I18n.t(p.type.toString().lowercase())}</span>" +
            "<span class=\"pet-sex $sexClass\">${I18n.t(p.sex.toString().lowercase())}</span>$sizeHtml" +
            "<div class=\"pet-name\"><h3>${p.name}$urgent</h3>$breedHtml</div>" +
            "<p class=\"pet-info\"><span class=\"pet-age\"><span class=\"label\">${I18n.t("age")}</span>" +
            "<span class=\"value\">${p.ageYears}${I18n.t("years")} ${p.ageMonths}${I18n.t("months")}</span></span>" +
            "<span class=\"pet-rescue-date\">$rescueDateHtml</span></p>" +
            "<p class=\"pet-status\">${p.status}</p></div></a>"
    }

    private fun updateSentinelState() {
        val sentinel = document.getElementById("pets-sentinel") ?: return
        sentinel.textContent = if (renderedCount < allPets.size) I18n.t("loadingMorePets") else ""
    }

    private fun renderNextBatch() {
        if (renderedCount >= allPets.size) return
        val container = document.getElementById("pets").unsafeCast<HTMLElement?>() ?: return
        val nextBatch = allPets.drop(renderedCount).take(PAGE_SIZE)
        container.asDynamic().insertAdjacentHTML("beforeend", nextBatch.joinToString("") { petCardHtml(it) })
        renderedCount += nextBatch.size
        updateSentinelState()
        if (renderedCount >= allPets.size) {
            scrollObserver?.disconnect()
            scrollObserver = null
        }
    }

    private fun setupInfiniteScroll() {
        scrollObserver?.disconnect()
        scrollObserver = null
        val sentinel = document.getElementById("pets-sentinel")
        if (sentinel == null || renderedCount >= allPets.size) return
        if (window.asDynamic().IntersectionObserver == null) return
        val callback: (dynamic) -> Unit = { entries ->
            val arr = entries as? Array<dynamic>
            if (arr?.any { it.isIntersecting == true } == true) renderNextBatch()
        }
        val options = js("({rootMargin: '600px 0px'})")
        scrollObserver = js("new IntersectionObserver(callback, options)")
        scrollObserver.observe(sentinel)
    }
}
