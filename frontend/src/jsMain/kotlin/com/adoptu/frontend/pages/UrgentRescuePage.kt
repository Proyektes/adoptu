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
                val lat = position.coords.latitude as? Double
                val lon = position.coords.longitude as? Double
                latitude = lat
                longitude = lon
                status?.textContent = I18n.t("locationCaptured")
                if (lat != null && lon != null) reverseGeocodeAndFillFields(lat, lon, status)
            },
            { _: dynamic -> status?.textContent = I18n.t("locationDenied") }
        )
    }

    // Fills country/state/city/street from a best-effort reverse geocode - exterior number and
    // reference notes are left for the reporter to type, since GPS accuracy can't tell them apart
    // from the neighbor's house. Silently leaves the fields blank on failure (no address found,
    // network error) rather than blocking submission - coordinates alone are still enough to page
    // nearby rescuers, see UrgentRescueService.resolveReportLocation.
    private fun reverseGeocodeAndFillFields(lat: Double, lon: Double, status: org.w3c.dom.Element?) {
        apiFetch("/api/urgent-reports/reverse-geocode?lat=$lat&lon=$lon")
            .then<Unit> { address: dynamic ->
                (document.getElementById("report-country") as? HTMLSelectElement)?.let {
                    val country = address.country?.toString()
                    if (!country.isNullOrBlank()) it.value = country
                }
                (address.state?.toString())?.let { (document.getElementById("report-state") as? HTMLInputElement)?.value = it }
                (address.city?.toString())?.let { (document.getElementById("report-city") as? HTMLInputElement)?.value = it }
                (address.street?.toString())?.let { (document.getElementById("report-street") as? HTMLInputElement)?.value = it }
                (address.houseNumber?.toString())?.let { (document.getElementById("report-exterior-number") as? HTMLInputElement)?.value = it }
            }
            .catch<Unit> { /* reverse geocode is best-effort - coordinates were already captured above */ }
    }

    private fun submit() {
        val msg = document.getElementById("message")
        val description = (document.getElementById("description") as? HTMLTextAreaElement)?.value ?: ""
        val dangerType = (document.getElementById("danger-type") as? HTMLSelectElement)?.value ?: "OTHER"
        val country = (document.getElementById("report-country") as? HTMLSelectElement)?.value
        val state = (document.getElementById("report-state") as? HTMLInputElement)?.value
        val city = (document.getElementById("report-city") as? HTMLInputElement)?.value
        val street = (document.getElementById("report-street") as? HTMLInputElement)?.value
        val exteriorNumber = (document.getElementById("report-exterior-number") as? HTMLInputElement)?.value
        val referenceNotes = (document.getElementById("report-reference-notes") as? HTMLInputElement)?.value
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
            "city" to city,
            "street" to street,
            "exteriorNumber" to exteriorNumber,
            "referenceNotes" to referenceNotes
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
    private var map: dynamic = null
    private var marker: dynamic = null
    private var circle: dynamic = null
    // Set while a zone-field value is being written *from* the map (reverse geocode after a
    // drag/capture), so that write doesn't re-trigger the zone-fields' own change listener and
    // bounce straight back into a forward geocode.
    private var suppressZoneFieldSync = false

    fun init() {
        initMap()

        apiFetch("/api/urgent-rescuers/me").then<Unit> { profile: dynamic ->
            hasExistingProfile = true
            (document.getElementById("urgent-rescuer-active") as? HTMLInputElement)?.checked = profile.active == true
            (document.getElementById("urgent-phone") as? HTMLInputElement)?.value = profile.phone?.toString() ?: ""
            (document.getElementById("radius-km") as? HTMLInputElement)?.value = profile.radiusKm?.toString() ?: "10"
            val lat = profile.latitude as? Double
            val lon = profile.longitude as? Double
            if (lat != null && lon != null) {
                setPin(lat, lon)
                reverseGeocodeAndFillZoneFields(lat, lon)
            }
        }.catch<Unit> { /* no profile yet - fine, first save creates one */ }

        document.getElementById("capture-location-btn")?.addEventListener("click", { captureLocation() })
        document.getElementById("radius-km")?.addEventListener("change", {
            if (circle != null) {
                circle.setRadius(radiusMeters())
                map.fitBounds(circle.getBounds(), json("maxZoom" to 15))
            }
        })
        document.getElementById("urgent-zone-country")?.addEventListener("change", { geocodeZoneFields() })
        document.getElementById("urgent-zone-city")?.addEventListener("change", { geocodeZoneFields() })
        document.getElementById("save-urgent-profile-btn")?.addEventListener("click", { save() })
    }

    private fun radiusMeters(): Double =
        (((document.getElementById("radius-km") as? HTMLInputElement)?.value?.toDoubleOrNull()) ?: 10.0) * 1000.0

    private fun initMap() {
        val leaflet = window.asDynamic().L ?: return
        map = leaflet.map("location-map").setView(leaflet.latLng(20.0, 0.0), 2)
        leaflet.tileLayer(
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
            json("attribution" to "&copy; OpenStreetMap contributors", "maxZoom" to 19)
        ).addTo(map)
    }

    // Creates the pin + coverage circle on first use, or just moves them afterwards. Centers/zooms
    // the map on it unless the move originated from dragging the pin itself (recenter = false),
    // since re-centering under the user's own drag gesture would fight their mouse.
    private fun setPin(lat: Double, lon: Double, recenter: Boolean = true) {
        latitude = lat
        longitude = lon
        val leaflet = window.asDynamic().L ?: return
        val point = leaflet.latLng(lat, lon)
        if (marker == null) {
            marker = leaflet.marker(point, json("draggable" to true)).addTo(map)
            marker.on("dragend", {
                val pos = marker.getLatLng()
                val newLat = pos.lat.unsafeCast<Double>()
                val newLon = pos.lng.unsafeCast<Double>()
                setPin(newLat, newLon, recenter = false)
                reverseGeocodeAndFillZoneFields(newLat, newLon)
            })
            circle = leaflet.circle(point, json("radius" to radiusMeters())).addTo(map)
        } else {
            marker.setLatLng(point)
            circle.setLatLng(point)
        }
        circle.setRadius(radiusMeters())
        // fitBounds rather than a fixed zoom - a 100+ km radius needs to zoom out much further
        // than a 1 km one for the circle's edge to actually be visible.
        if (recenter) map.fitBounds(circle.getBounds(), json("maxZoom" to 15))
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
                val lat = position.coords.latitude as? Double
                val lon = position.coords.longitude as? Double
                status?.textContent = I18n.t("locationCaptured")
                if (lat != null && lon != null) {
                    setPin(lat, lon)
                    reverseGeocodeAndFillZoneFields(lat, lon)
                }
            },
            { _: dynamic -> status?.textContent = I18n.t("locationDenied") }
        )
    }

    // Best-effort - leaves the zone fields as they were on failure (no address found, network
    // error) rather than clearing them; the map/coordinates are already the source of truth.
    private fun reverseGeocodeAndFillZoneFields(lat: Double, lon: Double) {
        apiFetch("/api/urgent-reports/reverse-geocode?lat=$lat&lon=$lon")
            .then<Unit> { address: dynamic ->
                suppressZoneFieldSync = true
                (document.getElementById("urgent-zone-country") as? HTMLSelectElement)?.let {
                    val country = address.country?.toString()
                    if (!country.isNullOrBlank()) it.value = country
                }
                (address.city?.toString())?.let { (document.getElementById("urgent-zone-city") as? HTMLInputElement)?.value = it }
                suppressZoneFieldSync = false
            }
            .catch<Unit> { suppressZoneFieldSync = false }
    }

    // Mirror of the above: typing/selecting a zone moves the pin instead. Leaves the pin where it
    // was on failure (no match, incomplete fields) rather than clearing it.
    private fun geocodeZoneFields() {
        if (suppressZoneFieldSync) return
        val country = (document.getElementById("urgent-zone-country") as? HTMLSelectElement)?.value
        val city = (document.getElementById("urgent-zone-city") as? HTMLInputElement)?.value
        if (country.isNullOrBlank() || city.isNullOrBlank()) return
        val encodedCountry = window.asDynamic().encodeURIComponent(country)
        val encodedCity = window.asDynamic().encodeURIComponent(city)
        apiFetch("/api/urgent-reports/geocode?country=$encodedCountry&city=$encodedCity")
            .then<Unit> { result: dynamic ->
                val lat = result.latitude as? Double
                val lon = result.longitude as? Double
                if (lat != null && lon != null) setPin(lat, lon)
            }
            .catch<Unit> { /* no match for that zone - leave the pin where it was */ }
    }

    private fun save() {
        val msg = document.getElementById("message")
        val active = (document.getElementById("urgent-rescuer-active") as? HTMLInputElement)?.checked ?: false
        val phone = (document.getElementById("urgent-phone") as? HTMLInputElement)?.value ?: ""
        val radiusKm = (document.getElementById("radius-km") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: 10.0
        val zoneCountry = (document.getElementById("urgent-zone-country") as? HTMLSelectElement)?.value
        val zoneCity = (document.getElementById("urgent-zone-city") as? HTMLInputElement)?.value

        if (phone.isBlank()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("phoneRequired")
            return
        }
        if (latitude == null || longitude == null) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("locationRequired")
            return
        }

        msg?.textContent = I18n.t("savingEllipsis")
        msg?.className = ""

        val profileBody = json(
            "phone" to phone,
            "inputMode" to "COORDINATES",
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
                empty?.setAttribute("data-i18n", "noUrgentPages")
                empty?.textContent = I18n.t("noUrgentPages")
                return@then
            }
            list.forEach { page -> container?.appendChild(buildCard(page)) }
        }.catch<Unit> {
            document.getElementById("pages-empty")?.apply {
                setAttribute("data-i18n", "noUrgentPages")
                textContent = I18n.t("noUrgentPages")
            }
        }
    }

    private fun buildCard(page: dynamic): HTMLElement {
        val card = document.createElement("div").unsafeCast<HTMLElement>()
        card.className = "card-bg profile-section"
        val referenceNotes = page.referenceNotes?.toString()
        val referenceNotesHtml = if (!referenceNotes.isNullOrBlank()) {
            "<p><em>${I18n.t("referenceNotes")}: ${CommonModule.escapeHtml(referenceNotes)}</em></p>"
        } else ""
        card.innerHTML = """
            <h2>${CommonModule.escapeHtml(page.dangerType?.toString())}</h2>
            <p>${CommonModule.escapeHtml(page.locationLabel?.toString())}</p>
            $referenceNotesHtml
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
                container.setAttribute("data-i18n", "noLeaderboardData")
                container.textContent = I18n.t("noLeaderboardData")
                return@then
            }
            container.removeAttribute("data-i18n")
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
