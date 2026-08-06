package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import kotlin.js.json

@JsExport
@JsName("RescuersPage")
object RescuersPageModule {
    fun init() {
        ApiClientModule.getRescuers().then<Unit> { rescuers -> render(rescuers) }.catch {
            document.getElementById("rescuers-container")?.innerHTML = "<p>${I18n.t("errorLoadingResults")}</p>"
        }
    }

    private fun render(rescuers: dynamic) {
        val container = document.getElementById("rescuers-container").unsafeCast<HTMLElement?>()
        val list = rescuers as? Array<dynamic>
        if (list == null || list.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("noRescuersFound")}</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { rescuer ->
            val name = CommonModule.escapeHtml(rescuer.displayName?.toString() ?: "")
            val country = rescuer.country?.toString()?.takeIf { it.isNotEmpty() }?.let { I18n.translateCountry(it) } ?: ""
            val petCount = rescuer.availablePetCount?.toString() ?: "0"
            "<a href=\"/rescuer/${rescuer.userId}\" class=\"rescuer-card\">" +
                "<div class=\"rescuer-card-icon\">🧑‍🤝‍🧑</div>" +
                "<div class=\"rescuer-card-info\"><h3>$name</h3>" +
                (if (country.isNotEmpty()) "<p class=\"location\">$country</p>" else "") +
                "<p class=\"rescuer-pet-count\">${I18n.t("availablePetsCount")}: $petCount</p></div></a>"
        }
    }
}

@JsExport
@JsName("RescuerDetailPage")
object RescuerDetailPageModule {
    private var rescuerId: Int = 0

    fun init() {
        val segments = window.location.pathname.split("/")
        val id = segments.lastOrNull { it.isNotEmpty() }
        if (id.isNullOrEmpty() || id.toIntOrNull() == null) {
            window.location.href = "/rescuers"
            return
        }
        rescuerId = id.toInt()

        ApiClientModule.getRescuerById(id).then<Unit> { rescuer ->
            ApiClientModule.me().then<Unit> { user -> render(rescuer, user) }.catch { render(rescuer, js("({authenticated: false})")) }
        }.catch {
            val container = document.getElementById("rescuer-detail").unsafeCast<HTMLElement?>()
            container?.innerHTML = "<p>${I18n.t("rescuerNotFound")}</p><a href=\"/rescuers\">${I18n.t("backToSearch")}</a>"
        }
    }

    private fun render(rescuer: dynamic, user: dynamic) {
        val container = document.getElementById("rescuer-detail").unsafeCast<HTMLElement?>()
        val name = CommonModule.escapeHtml(rescuer.displayName?.toString() ?: "")
        val country = rescuer.country?.toString()?.takeIf { it.isNotEmpty() }?.let { I18n.translateCountry(it) } ?: ""

        val sb = StringBuilder()
        sb.append("<div class=\"rescuer-detail-header\">")
        sb.append("<div class=\"rescuer-detail-icon\">🧑‍🤝‍🧑</div>")
        sb.append("<h1>$name</h1>")
        if (country.isNotEmpty()) sb.append("<p class=\"location\">$country</p>")
        sb.append("</div>")

        sb.append("<div class=\"rescuer-detail-body\">")

        val isAuthenticated = user.authenticated != false
        val isSelf = isAuthenticated && user.id?.toString() == rescuerId.toString()
        if (isAuthenticated && !isSelf) {
            sb.append("<h2>${I18n.t("volunteerForThisRescuer")}</h2>")
            sb.append("<p>${I18n.t("volunteerExplanation")}</p>")
            sb.append("<button type=\"button\" class=\"btn\" id=\"volunteer-btn\">${I18n.t("applyToVolunteerBtn")}</button>")

            sb.append("<h2>${I18n.t("sponsorThisRescuerTitle")}</h2>")
            sb.append("<p>${I18n.t("sponsorRescuerExplanation")}</p>")
            sb.append(sponsorFormHtml())
            sb.append("<div id=\"sponsor-form-message\"></div>")
        } else if (!isAuthenticated) {
            sb.append("<h2>${I18n.t("volunteerForThisRescuer")}</h2>")
            sb.append("<p>${I18n.t("loginToVolunteer")}</p>")
        }

        sb.append("<h2>${I18n.t("availablePets")}</h2>")
        val pets = (rescuer.pets as? Array<dynamic>) ?: arrayOf()
        if (pets.isEmpty()) {
            sb.append("<p>${I18n.t("noAvailablePetsForRescuer")}</p>")
        } else {
            sb.append("<div class=\"rescuer-pets-grid\">")
            sb.append(pets.joinToString("") { petCardHtml(it) })
            sb.append("</div>")
        }
        sb.append("</div>")

        container?.innerHTML = sb.toString()

        document.getElementById("volunteer-btn")?.addEventListener("click", { _: Event -> applyToVolunteer() })
        if (isAuthenticated && !isSelf) {
            document.getElementById("sponsor-type")?.addEventListener("change", { toggleSponsorFields() })
            document.getElementById("sponsor-form")?.addEventListener("submit", { e: Event ->
                e.preventDefault()
                submitSponsorOffer()
            })
        }
    }

    // General-fund offer - no petId, unlike PetDetailPageModule's per-pet sponsor form.
    private fun sponsorFormHtml(): String {
        return "<form id=\"sponsor-form\">" +
            "<label for=\"sponsor-type\">${I18n.t("sponsorTypeLabel")}</label>" +
            "<select id=\"sponsor-type\">" +
            "<option value=\"MONEY\">${I18n.t("sponsorTypeMoney")}</option>" +
            "<option value=\"IN_KIND\">${I18n.t("sponsorTypeInKind")}</option>" +
            "</select>" +
            "<div id=\"sponsor-money-fields\">" +
            "<label for=\"sponsor-amount\">${I18n.t("amountLabel")}</label>" +
            "<input type=\"number\" id=\"sponsor-amount\" min=\"0\" step=\"0.01\">" +
            "<label for=\"sponsor-currency\">${I18n.t("currencyLabel")}</label>" +
            "<select id=\"sponsor-currency\"><option value=\"USD\">USD</option><option value=\"MXN\">MXN</option><option value=\"EUR\">EUR</option></select>" +
            "</div>" +
            "<div id=\"sponsor-in-kind-fields\" class=\"hidden\">" +
            "<label for=\"sponsor-in-kind-description\">${I18n.t("inKindDescriptionLabel")}</label>" +
            "<textarea id=\"sponsor-in-kind-description\"></textarea>" +
            "</div>" +
            "<label for=\"sponsor-message\">${I18n.t("messageLabel")}</label>" +
            "<textarea id=\"sponsor-message\" required></textarea>" +
            "<button type=\"submit\" class=\"btn btn-secondary\">${I18n.t("sendOfferBtn")}</button></form>"
    }

    private fun toggleSponsorFields() {
        val isMoney = (document.getElementById("sponsor-type") as? HTMLSelectElement)?.value == "MONEY"
        (document.getElementById("sponsor-money-fields") as? HTMLElement)?.classList?.let { if (isMoney) it.remove("hidden") else it.add("hidden") }
        (document.getElementById("sponsor-in-kind-fields") as? HTMLElement)?.classList?.let { if (isMoney) it.add("hidden") else it.remove("hidden") }
    }

    private fun submitSponsorOffer() {
        val offerType = (document.getElementById("sponsor-type") as? HTMLSelectElement)?.value ?: "MONEY"
        val message = (document.getElementById("sponsor-message") as? HTMLTextAreaElement)?.value ?: ""
        val body = if (offerType == "MONEY") {
            val amount = (document.getElementById("sponsor-amount") as? HTMLInputElement)?.value?.toDoubleOrNull()
            val currency = (document.getElementById("sponsor-currency") as? HTMLSelectElement)?.value
            json("rescuerId" to rescuerId, "offerType" to offerType, "amount" to amount, "currency" to currency, "message" to message)
        } else {
            val description = (document.getElementById("sponsor-in-kind-description") as? HTMLTextAreaElement)?.value
            json("rescuerId" to rescuerId, "offerType" to offerType, "inKindDescription" to description, "message" to message)
        }
        ApiClientModule.createSponsorshipOffer(body).then<Unit> {
            (document.getElementById("sponsor-form-message") as? HTMLElement)?.let {
                it.className = "message success"
                it.textContent = I18n.t("sponsorOfferSent")
            }
            document.getElementById("sponsor-form")?.unsafeCast<HTMLElement>()?.style?.display = "none"
        }.catch { err: dynamic ->
            (document.getElementById("sponsor-form-message") as? HTMLElement)?.let {
                it.className = "message error"
                it.textContent = err?.message?.toString() ?: "Error"
            }
        }
    }

    private fun applyToVolunteer() {
        val btn = document.getElementById("volunteer-btn").unsafeCast<HTMLElement?>()
        ApiClientModule.applyToVolunteer(rescuerId).then<Unit> {
            (document.getElementById("message") as? HTMLElement)?.let {
                it.className = "message success"
                it.textContent = I18n.t("volunteerApplicationSent")
            }
            btn?.setAttribute("disabled", "true")
        }.catch { err: dynamic ->
            (document.getElementById("message") as? HTMLElement)?.let {
                it.className = "message error"
                it.textContent = err?.message?.toString() ?: "Error"
            }
        }
    }

    private fun petCardHtml(p: dynamic): String {
        val images = p.images as? Array<dynamic>
        val primaryImage = images?.firstOrNull { it.isPrimary == true } ?: images?.firstOrNull()
        val imageHtml = if (primaryImage != null) {
            "<img src=\"${primaryImage.imageUrl}\" alt=\"${CommonModule.escapeHtml(p.name?.toString() ?: "")}\" loading=\"lazy\">"
        } else {
            "<div class=\"pet-card-placeholder\">🐾</div>"
        }
        return "<a href=\"/pet/${p.id}\" class=\"pet-card\">$imageHtml<div class=\"pet-card-body\">" +
            "<div class=\"pet-name\"><h3>${CommonModule.escapeHtml(p.name?.toString() ?: "")}</h3></div>" +
            "</div></a>"
    }
}
