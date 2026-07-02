package com.adoptu.frontend.pages

import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

@JsExport
@JsName("SheltersPage")
object SheltersPageModule {
    fun init() {
        window.asDynamic().searchShelters = { search() }

        document.getElementById("search-btn")?.addEventListener("click", { search() })
        val debounced = CommonModule.debounce(500) { search() }
        listOf("search-state", "search-city", "search-zip", "search-neighborhood").forEach { id ->
            document.getElementById(id)?.addEventListener("input", { debounced() })
        }
    }

    private fun search() {
        val errorDiv = document.getElementById("shelters-error").unsafeCast<HTMLElement?>()
        val container = document.getElementById("shelters").unsafeCast<HTMLElement?>()
        errorDiv?.style?.display = "none"
        errorDiv?.textContent = ""
        container?.innerHTML = "<p>${I18n.t("loading")}</p>"

        val params = CommonModule.buildLocationSearchParams()
        if (params == null) {
            errorDiv?.style?.display = "block"
            errorDiv?.textContent = I18n.t("countryRequired")
            container?.innerHTML = ""
            return
        }

        window.asDynamic().fetch("/api/shelters?" + params.toString()).then { res: dynamic ->
            if (res.ok != true) {
                throw js("new Error('Failed to load shelters')")
            }
            res.json().then { shelters: dynamic -> renderShelters(shelters, container) }
        }.catch { _: dynamic ->
            errorDiv?.style?.display = "block"
            errorDiv?.textContent = I18n.t("errorLoadingShelters")
            container?.innerHTML = ""
        }
    }

    private fun renderShelters(shelters: dynamic, container: HTMLElement?) {
        val list = shelters as? Array<dynamic>
        if (list == null || list.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("noSheltersFound")}</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { shelter ->
            val location = listOfNotNull(
                shelter.city?.toString()?.takeIf { it.isNotEmpty() },
                shelter.state?.toString()?.takeIf { it.isNotEmpty() },
                I18n.translateCountry(shelter.country?.toString())?.takeIf { it.isNotEmpty() }
            ).joinToString(", ")
            val hasDonationInfo = (shelter.bankName != null) || (shelter.accountNumber != null) || (shelter.iban != null)

            val sb = StringBuilder()
            sb.append("<div class=\"shelter-card\">")
            sb.append("<h3>${shelter.name}</h3>")
            if (location.isNotEmpty()) sb.append("<p class=\"shelter-location\">$location</p>")
            sb.append("<p class=\"shelter-address\">${shelter.address}${if (shelter.zip != null) ", ${shelter.zip}" else ""}</p>")
            if (shelter.phone != null) sb.append("<p class=\"shelter-phone\"><strong>${I18n.t("phone")}:</strong> ${shelter.phone}</p>")
            if (shelter.email != null) sb.append("<p class=\"shelter-email\"><strong>${I18n.t("email")}:</strong> <a href=\"mailto:${shelter.email}\">${shelter.email}</a></p>")
            if (shelter.website != null) sb.append("<p class=\"shelter-website\"><a href=\"${shelter.website}\" target=\"_blank\">${I18n.t("visitWebsite")}</a></p>")
            if (shelter.fiscalId != null) sb.append("<p class=\"shelter-fiscal-id\"><strong>${I18n.t("fiscalId")}:</strong> ${shelter.fiscalId}</p>")
            if (hasDonationInfo) {
                sb.append("<div class=\"shelter-donation-info\"><h4>${I18n.t("donationInformation")}</h4>")
                if (shelter.bankName != null) sb.append("<p><strong>${I18n.t("bankName")}:</strong> ${shelter.bankName}</p>")
                if (shelter.accountHolderName != null) sb.append("<p><strong>${I18n.t("accountHolder")}:</strong> ${shelter.accountHolderName}</p>")
                if (shelter.accountNumber != null) sb.append("<p><strong>${I18n.t("accountNumber")}:</strong> ${shelter.accountNumber}</p>")
                if (shelter.iban != null) sb.append("<p><strong>IBAN:</strong> ${shelter.iban}</p>")
                if (shelter.swiftBic != null) sb.append("<p><strong>SWIFT/BIC:</strong> ${shelter.swiftBic}</p>")
                if (shelter.currency != null) sb.append("<p><strong>${I18n.t("currency")}:</strong> ${shelter.currency}</p>")
                sb.append("</div>")
            }
            if (shelter.description != null) sb.append("<p class=\"shelter-description\">${shelter.description}</p>")
            sb.append("</div>")
            sb.toString()
        }
    }
}
