package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.apiFetch
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.js.json

@JsExport
@JsName("ReportUrgentPage")
object ReportUrgentPageModule {
    private var latitude: Double? = null
    private var longitude: Double? = null

    fun init() {
        val emailRow = document.getElementById("reporter-contact-row") as? HTMLElement
        val phoneRow = document.getElementById("reporter-phone-row") as? HTMLElement
        val captchaRow = document.getElementById("captcha-row") as? HTMLElement

        ApiClientModule.me().then<Unit> { result: dynamic ->
            val authenticated = result.authenticated == true
            // A logged-in session already has a real, verified email on file - only ask
            // anonymous visitors for contact info and a CAPTCHA (see UrgentRescueService.submitReport).
            emailRow?.style?.display = if (authenticated) "none" else ""
            phoneRow?.style?.display = if (authenticated) "none" else ""
            captchaRow?.classList?.let { if (!authenticated) it.remove("hidden") else it.add("hidden") }
        }.catch<Unit> {
            captchaRow?.classList?.remove("hidden")
        }

        document.getElementById("use-my-location-btn")?.addEventListener("click", { captureLocation() })
        document.getElementById("submit-btn")?.addEventListener("click", { submit() })
    }

    private fun captureLocation() {
        val status = document.getElementById("location-status")
        status?.textContent = I18n.t("locating")
        val geolocation = window.navigator.asDynamic().geolocation
        if (geolocation == null) {
            status?.textContent = I18n.t("geolocationUnsupported")
            return
        }
        geolocation.getCurrentPosition(
            { position: dynamic ->
                latitude = position.coords.latitude as? Double
                longitude = position.coords.longitude as? Double
                status?.textContent = I18n.t("locationCaptured")
            },
            { _: dynamic -> status?.textContent = I18n.t("locationDenied") }
        )
    }

    private fun submit() {
        val msg = document.getElementById("message")
        val description = (document.getElementById("description") as? HTMLTextAreaElement)?.value ?: ""
        val dangerType = (document.getElementById("danger-type") as? HTMLSelectElement)?.value ?: "OTHER"
        val country = (document.getElementById("report-country") as? HTMLSelectElement)?.value
        val state = (document.getElementById("report-state") as? HTMLInputElement)?.value
        val city = (document.getElementById("report-city") as? HTMLInputElement)?.value
        val reporterEmail = (document.getElementById("reporter-email") as? HTMLInputElement)?.value
        val reporterPhone = (document.getElementById("reporter-phone") as? HTMLInputElement)?.value
        val captchaToken = window.asDynamic().turnstile?.getResponse()?.unsafeCast<String?>()

        if (description.isBlank()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("descriptionRequired")
            return
        }
        if (latitude == null && (country.isNullOrBlank() || city.isNullOrBlank())) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("locationRequired")
            return
        }

        msg?.textContent = I18n.t("submittingEllipsis")
        msg?.className = ""

        val body = json(
            "description" to description,
            "dangerType" to dangerType,
            "reporterEmail" to reporterEmail,
            "reporterPhone" to reporterPhone,
            "captchaToken" to captchaToken,
            "latitude" to latitude,
            "longitude" to longitude,
            "country" to country,
            "state" to state,
            "city" to city
        )

        apiFetch("/api/urgent-reports/submit", json("method" to "POST", "body" to JSON.stringify(body)))
            .then<Unit> { _: dynamic ->
                msg?.className = "message success"
                msg?.textContent = I18n.t("reportSubmitted")
            }
            .catch<Unit> { err: dynamic ->
                msg?.className = "message error"
                msg?.textContent = err?.message?.toString() ?: I18n.t("reportSubmitFailed")
            }
    }
}

@JsExport
@JsName("UrgentRescuerProfilePage")
object UrgentRescuerProfilePageModule {
    private var hasExistingProfile = false
    private var latitude: Double? = null
    private var longitude: Double? = null

    fun init() {
        apiFetch("/api/urgent-rescuers/me").then<Unit> { profile: dynamic ->
            hasExistingProfile = true
            (document.getElementById("urgent-rescuer-active") as? HTMLInputElement)?.checked = profile.active == true
            (document.getElementById("urgent-phone") as? HTMLInputElement)?.value = profile.phone?.toString() ?: ""
            (document.getElementById("radius-km") as? HTMLInputElement)?.value = profile.radiusKm?.toString() ?: "10"
            if (profile.inputMode == "ZONE") {
                (document.getElementById("mode-zone") as? HTMLInputElement)?.checked = true
                (document.getElementById("urgent-zone-country") as? HTMLSelectElement)?.value = profile.zoneCountry?.toString() ?: ""
                (document.getElementById("urgent-zone-city") as? HTMLInputElement)?.value = profile.zoneCity?.toString() ?: ""
                toggleMode(zone = true)
            }
            latitude = profile.latitude as? Double
            longitude = profile.longitude as? Double
        }.catch<Unit> { /* no profile yet - fine, first save creates one */ }

        document.getElementById("mode-coordinates")?.addEventListener("change", { toggleMode(zone = false) })
        document.getElementById("mode-zone")?.addEventListener("change", { toggleMode(zone = true) })
        document.getElementById("capture-location-btn")?.addEventListener("click", { captureLocation() })
        document.getElementById("save-urgent-profile-btn")?.addEventListener("click", { save() })
    }

    private fun toggleMode(zone: Boolean) {
        (document.getElementById("coordinates-fields") as? HTMLElement)?.style?.display = if (zone) "none" else ""
        (document.getElementById("zone-fields") as? HTMLElement)?.let { if (zone) it.classList.remove("hidden") else it.classList.add("hidden") }
    }

    private fun captureLocation() {
        val status = document.getElementById("coordinates-status")
        status?.textContent = I18n.t("locating")
        val geolocation = window.navigator.asDynamic().geolocation
        if (geolocation == null) {
            status?.textContent = I18n.t("geolocationUnsupported")
            return
        }
        geolocation.getCurrentPosition(
            { position: dynamic ->
                latitude = position.coords.latitude as? Double
                longitude = position.coords.longitude as? Double
                status?.textContent = I18n.t("locationCaptured")
            },
            { _: dynamic -> status?.textContent = I18n.t("locationDenied") }
        )
    }

    private fun save() {
        val msg = document.getElementById("message")
        val active = (document.getElementById("urgent-rescuer-active") as? HTMLInputElement)?.checked ?: false
        val phone = (document.getElementById("urgent-phone") as? HTMLInputElement)?.value ?: ""
        val isZoneMode = (document.getElementById("mode-zone") as? HTMLInputElement)?.checked ?: false
        val radiusKm = (document.getElementById("radius-km") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: 10.0
        val zoneCountry = (document.getElementById("urgent-zone-country") as? HTMLSelectElement)?.value
        val zoneCity = (document.getElementById("urgent-zone-city") as? HTMLInputElement)?.value

        if (phone.isBlank()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("phoneRequired")
            return
        }
        if (!isZoneMode && latitude == null) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("locationRequired")
            return
        }
        if (isZoneMode && (zoneCountry.isNullOrBlank() || zoneCity.isNullOrBlank())) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("locationRequired")
            return
        }

        msg?.textContent = I18n.t("savingEllipsis")
        msg?.className = ""

        val profileBody = json(
            "phone" to phone,
            "inputMode" to (if (isZoneMode) "ZONE" else "COORDINATES"),
            "latitude" to latitude,
            "longitude" to longitude,
            "radiusKm" to radiusKm,
            "zoneCountry" to zoneCountry,
            "zoneState" to null,
            "zoneCity" to zoneCity
        )
        val profileCall = if (hasExistingProfile) {
            apiFetch("/api/urgent-rescuers/me", json("method" to "PUT", "body" to JSON.stringify(profileBody)))
        } else {
            apiFetch("/api/urgent-rescuers/me", json("method" to "POST", "body" to JSON.stringify(profileBody)))
        }

        profileCall
            .then<dynamic> { _: dynamic ->
                hasExistingProfile = true
                apiFetch("/api/users/urgent-rescuer-profile", json("method" to "POST", "body" to JSON.stringify(json("activate" to active))))
            }
            .then<Unit> { _: dynamic ->
                msg?.className = "message success"
                msg?.textContent = I18n.t("saved")
            }
            .catch<Unit> { err: dynamic ->
                msg?.className = "message error"
                msg?.textContent = err?.message?.toString() ?: I18n.t("saveFailed")
            }
    }
}

@JsExport
@JsName("UrgentRescuerDashboardPage")
object UrgentRescuerDashboardPageModule {
    fun init() {
        apiFetch("/api/urgent-rescuers/my-pages").then<Unit> { pages: dynamic ->
            val container = document.getElementById("pages-container")
            val empty = document.getElementById("pages-empty")
            val list = pages.unsafeCast<Array<dynamic>>()
            if (list.isEmpty()) {
                empty?.textContent = I18n.t("noUrgentPages")
                return@then
            }
            list.forEach { page -> container?.appendChild(buildCard(page)) }
        }.catch<Unit> {
            document.getElementById("pages-empty")?.textContent = I18n.t("noUrgentPages")
        }
    }

    private fun buildCard(page: dynamic): HTMLElement {
        val card = document.createElement("div").unsafeCast<HTMLElement>()
        card.className = "card-bg profile-section"
        card.innerHTML = """
            <h2>${CommonModule.escapeHtml(page.dangerType?.toString())}</h2>
            <p>${CommonModule.escapeHtml(page.locationLabel?.toString())}</p>
            <p>${CommonModule.escapeHtml(page.description?.toString())}</p>
        """.trimIndent()

        val acceptBtn = document.createElement("button").unsafeCast<HTMLButtonElement>()
        acceptBtn.className = "btn"
        acceptBtn.textContent = I18n.t("accept")
        acceptBtn.addEventListener("click", {
            acceptBtn.disabled = true
            apiFetch("/api/urgent-rescuers/reports/${page.reportId}/accept", json("method" to "POST"))
                .then<Unit> { _: dynamic ->
                    CommonModule.showDonationPrompt(document.getElementById("pages-container"))
                    card.remove()
                }
                .catch<Unit> { _: dynamic ->
                    card.innerHTML += "<p class=\"message error\">${I18n.t("alreadyAccepted")}</p>"
                }
        })
        card.appendChild(acceptBtn)
        return card
    }
}

@JsExport
@JsName("UrgentRescuerLeaderboardPage")
object UrgentRescuerLeaderboardPageModule {
    fun init() {
        apiFetch("/api/urgent-rescuers/leaderboard").then<Unit> { entries: dynamic ->
            val container = document.getElementById("leaderboard-container") ?: return@then
            val list = entries.unsafeCast<Array<dynamic>>()
            if (list.isEmpty()) {
                container.textContent = I18n.t("noLeaderboardData")
                return@then
            }
            val ol = document.createElement("ol")
            list.forEach { entry ->
                val li = document.createElement("li")
                li.textContent = "${entry.displayName} - ${entry.acceptedCount}"
                ol.appendChild(li)
            }
            container.appendChild(ol)
        }
    }
}

@JsExport
@JsName("UrgentRescueAcceptPage")
object UrgentRescueAcceptPageModule {
    fun init() {
        val params = js("new URLSearchParams(window.location.search)")
        val token = params.get("token") as? String
        if (token.isNullOrBlank()) {
            showError(null)
            return
        }
        apiFetch("/api/urgent-reports/accept?token=" + window.asDynamic().encodeURIComponent(token))
            .then<Unit> { _: dynamic -> showSuccess() }
            .catch<Unit> { err: dynamic -> showError(err?.message?.toString()) }
    }

    private fun showSuccess() {
        document.getElementById("accept-success")?.className = "verification-success"
        document.getElementById("accept-error")?.className = "verification-error hidden"
        CommonModule.showDonationPrompt(document.getElementById("accept-success"))
    }

    private fun showError(message: String?) {
        document.getElementById("accept-error")?.className = "verification-error"
        document.getElementById("accept-success")?.className = "verification-success hidden"
        if (!message.isNullOrBlank()) {
            document.getElementById("accept-error-message")?.textContent = message
        }
    }
}
