package com.adoptu.frontend.pages

import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("SterilizationLocationsPage")
object SterilizationLocationsPageModule {
    fun init() {
        window.asDynamic().searchLocations = { search() }
        document.getElementById("search-btn")?.addEventListener("click", { search() })
        val debounced = CommonModule.debounce(500) { search() }
        listOf("search-state", "search-city", "search-zip", "search-neighborhood").forEach { id ->
            document.getElementById(id)?.addEventListener("input", { debounced() })
        }
    }

    private fun search() {
        val container = document.getElementById("locations-container").unsafeCast<HTMLElement?>()
        val params = CommonModule.buildLocationSearchParams()
        if (params == null) {
            container?.innerHTML = "<p>${I18n.t("pleaseSelectCountry")}</p>"
            return
        }
        window.asDynamic().fetch("/api/sterilization-locations?" + params.toString()).then { res: dynamic ->
            res.json().then { locations: dynamic -> render(locations, container) }
        }.catch { _: dynamic ->
            container?.innerHTML = "<p>${I18n.t("errorLoadingLocations")}</p>"
        }
    }

    private fun render(data: dynamic, container: HTMLElement?) {
        val list = data as? Array<dynamic>
        if (list == null || list.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("noLocationsFound")}</p>"
            return
        }
        val cards = list.joinToString("") { loc -> locationCard(loc, includeActions = false) }
        container?.innerHTML = "<div class=\"location-list\">$cards</div>"
    }
}

private fun locationCard(loc: dynamic, includeActions: Boolean): String {
    val name = if (includeActions) CommonModule.escapeHtml(loc.name?.toString()) else loc.name.toString()
    val address = if (includeActions) CommonModule.escapeHtml(loc.address?.toString()) else loc.address.toString()
    val city = if (includeActions) CommonModule.escapeHtml(loc.city?.toString()) else loc.city.toString()
    val state = loc.state?.toString()?.takeIf { it.isNotEmpty() }?.let { if (includeActions) CommonModule.escapeHtml(it) else it }
    val translatedCountry = I18n.translateCountry(loc.country?.toString())
    val country = if (includeActions) CommonModule.escapeHtml(translatedCountry) else translatedCountry.toString()
    val sb = StringBuilder()
    sb.append("<div class=\"location-card card-bg\"><h3>$name</h3>")
    sb.append("<p class=\"location-address\">$address, $city${if (state != null) ", $state" else ""}, $country</p>")
    if (loc.phone != null) sb.append("<p class=\"location-phone\"><strong>${I18n.t("phone")}:</strong> ${if (includeActions) CommonModule.escapeHtml(loc.phone.toString()) else loc.phone}</p>")
    if (loc.email != null) sb.append("<p class=\"location-email\"><strong>${I18n.t("email")}:</strong> ${if (includeActions) CommonModule.escapeHtml(loc.email.toString()) else loc.email}</p>")
    if (loc.website != null) sb.append("<p class=\"location-website\"><a href=\"${loc.website}\" target=\"_blank\">${I18n.t("website")}</a></p>")
    if (loc.description != null) sb.append("<p class=\"location-description\">${if (includeActions) CommonModule.escapeHtml(loc.description.toString()) else loc.description}</p>")
    if (includeActions) {
        sb.append("<div class=\"pet-card-actions\"><button class=\"btn\" onclick=\"editLocation(${loc.id})\">${I18n.t("edit")}</button> ")
        sb.append("<button class=\"btn btn-danger\" onclick=\"deleteLocation(${loc.id})\">${I18n.t("delete")}</button></div>")
    }
    sb.append("</div>")
    return sb.toString()
}

@JsExport
@JsName("AdminSterilizationLocationsPage")
object AdminSterilizationLocationsPageModule {
    private var editingId: Int? = null

    fun init() {
        window.asDynamic().showForm = { showForm() }
        window.asDynamic().hideForm = { hideForm() }
        window.asDynamic().editLocation = { id: Int -> editLocation(id) }
        window.asDynamic().deleteLocation = { id: Int -> deleteLocation(id) }

        document.getElementById("location-form")?.addEventListener("submit", { e: Event -> onSubmit(e) })
        loadLocations()
    }

    private fun loadCountries(): dynamic =
        window.asDynamic().fetch("/api/sterilization-locations/countries").then { res: dynamic ->
            res.json().then { data: dynamic ->
                val select = document.getElementById("form-country") as? HTMLSelectElement
                val countries = data.countries as? Array<dynamic> ?: arrayOf()
                val options = countries.joinToString("") { c ->
                    "<option value=\"$c\">${CommonModule.escapeHtml(I18n.translateCountry(c.toString()))}</option>"
                }
                select?.innerHTML = "<option value=\"\">${I18n.t("selectCountry")}</option>$options"
            }
        }

    private fun loadLocations() {
        val container = document.getElementById("locations-container").unsafeCast<HTMLElement?>()
        window.asDynamic().fetch("/api/admin/sterilization-locations").then { res: dynamic ->
            res.json().then { data: dynamic ->
                val list = data as? Array<dynamic>
                if (list == null || list.isEmpty()) {
                    container?.innerHTML = "<p>${I18n.t("noLocationsFound")}</p>"
                    return@then
                }
                val cards = list.joinToString("") { loc -> locationCard(loc, includeActions = true) }
                container?.innerHTML = "<div class=\"location-list\">$cards</div>"
            }
        }
    }

    private fun showForm() {
        editingId = null
        (document.getElementById("location-form") as? HTMLFormElement)?.reset()
        (document.getElementById("form-modal") as? HTMLElement)?.style?.display = "flex"
        loadCountries()
    }

    private fun hideForm() {
        (document.getElementById("form-modal") as? HTMLElement)?.style?.display = "none"
        editingId = null
    }

    private fun editLocation(id: Int) {
        editingId = id
        window.asDynamic().fetch("/api/sterilization-locations/$id").then { res: dynamic ->
            res.json().then { loc: dynamic ->
                loadCountries().then<Unit> {
                    val form = document.getElementById("location-form") as HTMLFormElement
                    (form.asDynamic().name as HTMLInputElement).value = loc.name.toString()
                    (document.getElementById("form-country") as HTMLSelectElement).value = loc.country.toString()
                    (form.asDynamic().state as HTMLInputElement).value = loc.state?.toString() ?: ""
                    (form.asDynamic().city as HTMLInputElement).value = loc.city.toString()
                    (form.asDynamic().address as HTMLInputElement).value = loc.address.toString()
                    (form.asDynamic().zip as HTMLInputElement).value = loc.zip?.toString() ?: ""
                    (form.asDynamic().phone as HTMLInputElement).value = loc.phone?.toString() ?: ""
                    (form.asDynamic().email as HTMLInputElement).value = loc.email?.toString() ?: ""
                    (form.asDynamic().website as HTMLInputElement).value = loc.website?.toString() ?: ""
                    (form.asDynamic().description as HTMLTextAreaElement).value = loc.description?.toString() ?: ""
                    (document.getElementById("form-modal") as HTMLElement).style.display = "flex"
                }
            }
        }
    }

    private fun deleteLocation(id: Int) {
        if (!window.confirm(I18n.t("confirmDelete"))) return
        window.asDynamic().fetch("/api/admin/sterilization-locations/$id", js("({method: 'DELETE'})")).then { _: dynamic ->
            loadLocations()
        }
    }

    private fun onSubmit(e: Event) {
        e.preventDefault()
        val form = document.getElementById("location-form") as HTMLFormElement
        val data = js("({})")
        data.name = (form.asDynamic().name as HTMLInputElement).value
        data.country = (document.getElementById("form-country") as HTMLSelectElement).value
        data.state = (form.asDynamic().state as HTMLInputElement).value.ifEmpty { null }
        data.city = (form.asDynamic().city as HTMLInputElement).value
        data.address = (form.asDynamic().address as HTMLInputElement).value
        data.zip = (form.asDynamic().zip as HTMLInputElement).value.ifEmpty { null }
        data.phone = (form.asDynamic().phone as HTMLInputElement).value.ifEmpty { null }
        data.email = (form.asDynamic().email as HTMLInputElement).value.ifEmpty { null }
        data.website = (form.asDynamic().website as HTMLInputElement).value.ifEmpty { null }
        data.description = (form.asDynamic().description as HTMLTextAreaElement).value.ifEmpty { null }

        val id = editingId
        val method = if (id != null) "PUT" else "POST"
        val url = if (id != null) "/api/admin/sterilization-locations/$id" else "/api/admin/sterilization-locations"

        window.asDynamic().fetch(url, js("({method: method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)})")).then { res: dynamic ->
            if (res.ok == true) {
                hideForm()
                loadLocations()
            } else {
                document.getElementById("message")?.innerHTML = "<div class=\"message error\">${I18n.t("errorSaving")}</div>"
            }
        }
    }
}
