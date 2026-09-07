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
    // Contextual donation ask at a goodwill moment (adoption request submitted, urgent-rescue/
    // lost-found accepted) - per STRATEGY.md's "apadrina un rescate" plan, this is deliberately
    // NOT a new payment integration (no credentials to manage): it's the same paypal.me link
    // already in the nav, just surfaced where someone has just done something good instead of
    // buried in a menu nobody clicks. Dismissible, never blocks the actual success flow.
    fun showDonationPrompt(afterElement: Element?) {
        val target = afterElement ?: return
        if (target.parentElement?.querySelector(".donation-prompt") != null) return // already shown once
        val banner = document.createElement("div")
        banner.className = "donation-prompt"
        banner.innerHTML = "<p>${I18n.t("donationPromptText")}</p>" +
            "<a href=\"https://paypal.me/adoptu/50\" target=\"_blank\" class=\"btn\">${I18n.t("donationPromptCta")}</a> " +
            "<button type=\"button\" class=\"btn btn-secondary donation-dismiss\">${I18n.t("dismiss")}</button>"
        target.parentElement?.insertBefore(banner, target.nextSibling)
        banner.querySelector(".donation-dismiss")?.addEventListener("click", { banner.remove() })
    }

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

    // Delegated click handler backing every data-action="fnName" [data-arg="..."] [data-arg2="..."]
    // element - replaces per-element onclick="..." attributes, which a nonce-based CSP script-src
    // can't allow (nonces apply to <script> elements, not inline event-handler attributes) without
    // a separate, broader script-src-attr 'unsafe-inline' allowance. One delegated listener on
    // document also survives elements being replaced via innerHTML - no per-render re-attachment.
    fun initClickActions() {
        document.addEventListener("click", { event ->
            val origin = event.target as? Element ?: return@addEventListener
            val el = origin.closest("[data-action]") as? HTMLElement ?: return@addEventListener
            when (val action = el.getAttribute("data-action")) {
                null -> {}
                "hide-self" -> el.style.display = "none"
                else -> {
                    val fn = window.asDynamic()[action]
                    if (jsTypeOf(fn) == "function") {
                        val arg = el.getAttribute("data-arg")
                        val arg2 = el.getAttribute("data-arg2")
                        when {
                            arg2 != null -> fn(arg, arg2)
                            arg != null -> fn(arg)
                            else -> fn()
                        }
                    }
                }
            }
        })
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

        // Handle hamburger menu (mobile-only, see _layout.scss's max-width:600px breakpoint)
        val hamburgerMenus = document.querySelectorAll(".hamburger-menu")
        for (i in 0 until hamburgerMenus.length) {
            val hamburgerMenu = hamburgerMenus.item(i)?.unsafeCast<HTMLElement>() ?: continue
            val btn = hamburgerMenu.querySelector(".hamburger-btn") as? HTMLElement

            btn?.addEventListener("click", { e ->
                e.preventDefault()
                e.stopPropagation()
                hamburgerMenu.classList.toggle("open")
            })
        }

        // Close dropdowns when clicking outside
        document.addEventListener("click", { _ ->
            val dropdowns = document.querySelectorAll(".user-dropdown, .resources-dropdown-content")
            for (j in 0 until dropdowns.length) {
                val dd = dropdowns.item(j)?.unsafeCast<HTMLElement>() ?: continue
                dd.style.display = "none"
            }
            val hamburgerMenus2 = document.querySelectorAll(".hamburger-menu.open")
            for (j in 0 until hamburgerMenus2.length) {
                hamburgerMenus2.item(j)?.unsafeCast<HTMLElement>()?.classList?.remove("open")
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

    // Replaces the window.onCountryChange/buildLocationSearchParams inline <script> that used to
    // live in LocationSearchFilters.kt (a static page has no per-response CSP nonce to stamp on
    // an inline script) - called unconditionally from Main.kt, no-ops when the page has no
    // #search-country element (Shelters/Photographers/SterilizationLocations/TemporalHome only).
    private const val SELECTED_COUNTRY_KEY = "adoptuSelectedCountry"

    fun initLocationSearchFilters() {
        val countrySelect = document.getElementById("search-country") as? HTMLSelectElement ?: return

        fun onCountryChange() {
            val hasCountry = countrySelect.value.isNotEmpty()
            if (hasCountry) {
                try { window.localStorage.setItem(SELECTED_COUNTRY_KEY, countrySelect.value) } catch (e: dynamic) {}
            }
            for (id in listOf("search-state", "search-city", "search-zip", "search-neighborhood")) {
                val el = document.getElementById(id) as? HTMLInputElement ?: continue
                el.disabled = !hasCountry
                if (!hasCountry) el.value = ""
            }
            (document.querySelector(".location-search-hint") as? HTMLElement)?.style?.display = if (hasCountry) "none" else ""
        }

        window.asDynamic().onCountryChange = { onCountryChange() }
        window.asDynamic().buildLocationSearchParams = {
            val country = countrySelect.value
            if (country.isEmpty()) {
                null
            } else {
                val params = js("new URLSearchParams()")
                params.append("country", country)
                for (id in listOf("search-state" to "state", "search-city" to "city", "search-zip" to "zip", "search-neighborhood" to "neighborhood")) {
                    val value = (document.getElementById(id.first) as? HTMLInputElement)?.value
                    if (!value.isNullOrEmpty()) params.append(id.second, value)
                }
                params
            }
        }

        countrySelect.addEventListener("change", { onCountryChange() })
        if (countrySelect.value.isEmpty()) {
            val saved = try { window.localStorage.getItem(SELECTED_COUNTRY_KEY) } catch (e: dynamic) { null }
            if (!saved.isNullOrEmpty()) countrySelect.value = saved
        }
        onCountryChange()
    }

    // Replaces the per-response server-rendered nav (UIRoutes.kt's getNavParams/NavParams,
    // pre-static-site) - every [data-auth] element in Shared.kt's commonNav() starts hidden
    // (.hidden default in style.scss) and gets shown here based on GET /api/auth/me, matching the
    // old isLoggedIn/isAdmin/isRescuerOrAdmin/isTemporalHomeOrAdmin gating exactly. Also enforces
    // the admin-only pages' old server-side redirect (data-auth-required="admin" on <body>,
    // set by AdminPage.kt/AdminSheltersPage.kt/SterilizationLocationsPage.kt's admin page).
    fun initAuthNav() {
        ApiClientModule.me().then<Unit> { result: dynamic ->
            val authenticated = result.authenticated == true
            val roles = (result.activeRoles as? Array<String>) ?: emptyArray()
            val isAdmin = roles.contains("ADMIN")
            val isRescuer = isAdmin || roles.contains("RESCUER")
            val isTemporalHome = isAdmin || roles.contains("TEMPORAL_HOME")
            val isUrgentRescuer = isAdmin || roles.contains("URGENT_RESCUER")

            fun matches(auth: String): Boolean = when (auth) {
                "guest" -> !authenticated
                "user" -> authenticated
                "admin" -> isAdmin
                "rescuer" -> isRescuer
                "temporal-home" -> isTemporalHome
                "urgent-rescuer" -> isUrgentRescuer
                else -> false
            }

            // Toggle the .hidden class, not style.display directly - every [data-auth] element
            // is already marked class="hidden" in the static HTML (Shared.kt's commonNav()), and
            // setting style.display = "" only clears an inline override, it doesn't un-hide an
            // element hidden by a stylesheet class rule.
            val gated = document.querySelectorAll("[data-auth]")
            gated.forEachElement { el ->
                val required = el.getAttribute("data-auth") ?: return@forEachElement
                if (matches(required)) el.classList.remove("hidden") else el.classList.add("hidden")
            }

            val requiredForPage = document.body?.getAttribute("data-auth-required")
            if (requiredForPage != null && !matches(requiredForPage)) {
                window.location.href = if (authenticated) "/" else "/login"
            }
        }.catch<Unit> {
            // /api/auth/me itself failing means "not authenticated" - leave every [data-auth]
            // element hidden (its default state) rather than throwing, same fallback the old
            // server-side NavParams() default used.
            val requiredForPage = document.body?.getAttribute("data-auth-required")
            if (requiredForPage != null) window.location.href = "/login"
        }
    }

    // Populates the footer's #deploy-sequence span from GET /api/version - unauthenticated, so a
    // plain fetch (not apiFetch's 401-retry wrapper, which exists for authenticated endpoints
    // only). Must stay under /api/ - CloudFront's app distribution only proxies /api/* paths to
    // the backend (see infra/cloudfront.tf); a bare /health would 404 against the static site's
    // own S3 origin instead. Silently leaves the span blank on failure rather than showing a
    // stale/placeholder value.
    fun initDeploySequence() {
        window.asDynamic().fetch("/api/version").then { res: dynamic ->
            res.json()
        }.then { body: dynamic ->
            val sequence = body.deploySequence?.toString()
            if (!sequence.isNullOrBlank()) {
                document.getElementById("deploy-sequence")?.textContent = "#$sequence"
            }
        }.catch { }
    }

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