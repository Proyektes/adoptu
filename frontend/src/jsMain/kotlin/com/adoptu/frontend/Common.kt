package com.adoptu.frontend

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import kotlin.js.Promise

fun NodeList.forEachElement(action: (Element) -> Unit) {
    for (i in 0 until length) {
        (item(i) as? Element)?.let(action)
    }
}

@JsExport
@JsName("Common")
object CommonModule {
    fun onCountryChange() {
        val countrySelect = window.document.getElementById("profile-country")
        val stateContainer = window.document.getElementById("state-container")
        if (countrySelect != null && stateContainer != null) {
            val country = (countrySelect as HTMLSelectElement).value
            if (country == "US") {
                try { stateContainer.unsafeCast<HTMLElement>().style.display = "block" } catch (e: dynamic) {}
            } else {
                try { stateContainer.unsafeCast<HTMLElement>().style.display = "none" } catch (e: dynamic) {}
            }
        }
    }

    fun initI18n(userLanguage: String?): Promise<dynamic> {
        val lang = userLanguage ?: window.localStorage.getItem("preferredLanguage") ?: "en"
        return I18n.loadLang(lang).then<dynamic> {
            I18n.updatePage()
            I18n.updateActiveLangOption()
        }
    }

    fun initDropdowns() {
        // Handle user menu dropdown
        val userMenus = document.querySelectorAll(".user-menu")
        for (i in 0 until userMenus.length) {
            val userMenu = userMenus.item(i)?.unsafeCast<HTMLElement>() ?: continue
            val dropdown = userMenu.querySelector(".user-dropdown") as? HTMLElement
            val avatar = userMenu.querySelector(".user-avatar") as? HTMLElement
            
            avatar?.addEventListener("click", { e ->
                e.preventDefault()
                e.stopPropagation()
                dropdown?.let {
                    val isVisible = it.style.display == "block"
                    it.style.display = if (isVisible) "none" else "block"
                }
            })
        }

        // Handle resources dropdown
        val resourceMenus = document.querySelectorAll(".resources-dropdown")
        for (i in 0 until resourceMenus.length) {
            val resMenu = resourceMenus.item(i)?.unsafeCast<HTMLElement>() ?: continue
            val dropdown = resMenu.querySelector(".resources-dropdown-content") as? HTMLElement
            val btn = resMenu.querySelector(".resources-dropbtn") as? HTMLElement
            
            btn?.addEventListener("click", { e ->
                e.preventDefault()
                e.stopPropagation()
                dropdown?.let {
                    val isVisible = it.style.display == "block"
                    it.style.display = if (isVisible) "none" else "block"
                }
            })
        }

        // Close dropdowns when clicking outside
        document.addEventListener("click", { _ ->
            val dropdowns = document.querySelectorAll(".user-dropdown, .resources-dropdown-content")
            for (j in 0 until dropdowns.length) {
                val dd = dropdowns.item(j)?.unsafeCast<HTMLElement>() ?: continue
                dd.style.display = "none"
            }
        })
    }

    fun checkProfileCompletion(user: dynamic): dynamic {
        val hasProfile = user.displayName != null && user.displayName.toString().isNotEmpty()
        val hasCountry = user.country != null && user.country.toString().isNotEmpty()
        return js("({hasProfile: hasProfile, hasCountry: hasCountry})")
    }

    fun escapeHtml(s: String?): String {
        if (s == null) return ""
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
    }

    fun debounce(waitMs: Int, action: () -> Unit): () -> Unit {
        var timeoutId: Int = -1
        return {
            if (timeoutId != -1) window.clearTimeout(timeoutId)
            timeoutId = window.setTimeout({ action() }, waitMs)
        }
    }

    fun buildLocationSearchParams(): dynamic = window.asDynamic().buildLocationSearchParams()

    private const val COUNTRY_STORAGE_KEY = "adoptu.selectedCountry"

    // Defaults a country <select>, in priority order: the last country picked anywhere on
    // the site (localStorage), then the logged-in user's profile country, then CloudFront's
    // IP-based geolocation header (via /api/detect-country) with the browser's own locale as
    // a fallback for requests that bypass CloudFront (e.g. local dev). Keeps localStorage in
    // sync as the user changes the selection so every other country selector reuses the choice.
    fun initCountrySelect(selectId: String, onApplied: () -> Unit = {}): Promise<Unit> {
        val select = document.getElementById(selectId) as? HTMLSelectElement ?: return Promise.resolve(Unit)

        select.addEventListener("change", {
            val value = select.value
            if (value.isNotEmpty()) window.localStorage.setItem(COUNTRY_STORAGE_KEY, value)
        })

        val stored = window.localStorage.getItem(COUNTRY_STORAGE_KEY)
        if (!stored.isNullOrEmpty()) {
            select.value = stored
            onApplied()
            return Promise.resolve(Unit)
        }

        // onApplied() is the source of truth for "country determination is complete" -
        // it fires exactly once, on every path, including total failure. Callers must not
        // rely on the timing of the returned Promise itself: a Promise resolved from inside
        // a conditional's non-Promise branch can resolve before a sibling branch's nested
        // fetch actually completes, so this deliberately does not chain loadPets()-style
        // follow-up work off the return value.
        return ApiClientModule.me().then<Boolean> { user ->
            val country = user.country?.toString()
            if (user.authenticated != false && !country.isNullOrEmpty()) {
                select.value = country
                window.localStorage.setItem(COUNTRY_STORAGE_KEY, country)
                true
            } else {
                false
            }
        }.catch<Boolean> { false }.then<Unit> { hasCountry ->
            if (hasCountry) {
                onApplied()
            } else {
                ApiClientModule.detectCountry(window.navigator.language).then<Unit> { detected ->
                    val detectedCountry = detected.country?.toString()
                    if (!detectedCountry.isNullOrEmpty()) {
                        select.value = detectedCountry
                        window.localStorage.setItem(COUNTRY_STORAGE_KEY, detectedCountry)
                    }
                    onApplied()
                }.catch<Unit> { onApplied() }
            }
        }
    }
}