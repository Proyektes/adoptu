package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

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
    fun init() {
        val segments = window.location.pathname.split("/")
        val id = segments.lastOrNull { it.isNotEmpty() }
        if (id.isNullOrEmpty() || id.toIntOrNull() == null) {
            window.location.href = "/rescuers"
            return
        }

        ApiClientModule.getRescuerById(id).then<Unit> { rescuer -> render(rescuer) }.catch {
            val container = document.getElementById("rescuer-detail").unsafeCast<HTMLElement?>()
            container?.innerHTML = "<p>${I18n.t("rescuerNotFound")}</p><a href=\"/rescuers\">${I18n.t("backToSearch")}</a>"
        }
    }

    private fun render(rescuer: dynamic) {
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
