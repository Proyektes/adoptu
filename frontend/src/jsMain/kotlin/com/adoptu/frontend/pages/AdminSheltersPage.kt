package com.adoptu.frontend.pages

import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("AdminSheltersPage")
object AdminSheltersPageModule {
    private var editingId: Int? = null

    fun init() {
        window.asDynamic().editShelter = { id: Int -> editShelter(id) }
        window.asDynamic().deleteShelter = { id: Int -> deleteShelter(id) }
        window.asDynamic().cancelEdit = { clearForm() }
        document.getElementById("cancel-btn")?.addEventListener("click", { clearForm() })

        val countrySelect = document.getElementById("filter-country") as? HTMLSelectElement
        countrySelect?.addEventListener("change", { loadShelters() })
        document.getElementById("filter-state")?.addEventListener("change", { loadShelters() })

        val stateSelect = document.getElementById("filter-state") as? HTMLSelectElement
        stateSelect?.innerHTML = "<option value=\"\">${I18n.t("allStates")}</option>"
        stateSelect?.disabled = true

        document.getElementById("shelter-form")?.addEventListener("submit", { e: Event -> onSubmit(e) })
    }

    private fun loadShelters() {
        val country = (document.getElementById("filter-country") as? HTMLSelectElement)?.value ?: ""
        val state = (document.getElementById("filter-state") as? HTMLSelectElement)?.value ?: ""
        val container = document.getElementById("shelters")
        val stateSelect = document.getElementById("filter-state") as? HTMLSelectElement

        if (country.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("selectCountryToFilter")}</p>"
            return
        }

        stateSelect?.innerHTML = "<option value=\"\">${I18n.t("allStates")}</option>"
        stateSelect?.disabled = false
        window.asDynamic().fetch("/api/shelters/countries/" + window.asDynamic().encodeURIComponent(country) + "/states").then { statesRes: dynamic ->
            if (statesRes.ok == true) {
                statesRes.json().then { data: dynamic ->
                    val states = data.states as? Array<dynamic>
                    states?.forEach { s ->
                        val option = document.createElement("option")
                        option.asDynamic().value = s
                        option.textContent = s.toString()
                        stateSelect?.appendChild(option)
                    }
                }
            }
        }

        val params = js("new URLSearchParams()")
        params.append("country", country)
        if (state.isNotEmpty()) params.append("state", state)

        window.asDynamic().fetch("/api/shelters?" + params.toString()).then { output: dynamic ->
            if (output.ok != true) throw js("new Error('Failed to load shelters')")
            output.json().then { shelters: dynamic -> renderShelters(shelters) }
        }.catch { _: dynamic ->
            showMessage(I18n.t("errorLoadingShelters"), "error")
        }
    }

    private fun renderShelters(data: dynamic) {
        val container = document.getElementById("shelters")
        val list = data as? Array<dynamic>
        if (list == null || list.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("noSheltersFound")}</p>"
            return
        }
        val rows = list.joinToString("") { s ->
            val contact = s.phone ?: s.email ?: "-"
            "<tr><td><strong>${CommonModule.escapeHtml(s.name?.toString())}</strong></td>" +
                "<td>${CommonModule.escapeHtml(s.city?.toString() ?: "")}, ${CommonModule.escapeHtml(s.state?.toString() ?: "")}, ${CommonModule.escapeHtml(I18n.translateCountry(s.country?.toString()) ?: "")}</td>" +
                "<td>${CommonModule.escapeHtml(contact.toString())}</td>" +
                "<td><button class=\"btn btn-secondary\" onclick=\"editShelter(${s.id})\">${I18n.t("edit")}</button> " +
                "<button class=\"btn btn-danger\" onclick=\"deleteShelter(${s.id})\">${I18n.t("delete")}</button></td></tr>"
        }
        container?.innerHTML = "<table class=\"admin-table\"><thead><tr><th>${I18n.t("name")}</th><th>${I18n.t("location")}</th>" +
            "<th>${I18n.t("contact")}</th><th>${I18n.t("actions")}</th></tr></thead><tbody>$rows</tbody></table>"
    }

    private fun showMessage(msg: String, type: String) {
        val div = document.getElementById("message")
        div?.textContent = msg
        div?.className = if (type == "error") "error-message" else "success-message"
        div?.asDynamic()?.style?.display = "block"
    }

    private fun getFormData(): dynamic {
        val data = js("({})")
        data.name = (document.getElementById("name") as HTMLInputElement).value
        data.country = (document.getElementById("country") as HTMLSelectElement).value
        data.state = (document.getElementById("state") as HTMLInputElement).value.ifEmpty { null }
        data.city = (document.getElementById("city") as HTMLInputElement).value
        data.address = (document.getElementById("address") as HTMLInputElement).value
        data.zip = (document.getElementById("zip") as HTMLInputElement).value.ifEmpty { null }
        data.phone = (document.getElementById("phone") as HTMLInputElement).value.ifEmpty { null }
        data.email = (document.getElementById("email") as HTMLInputElement).value.ifEmpty { null }
        data.website = (document.getElementById("website") as HTMLInputElement).value.ifEmpty { null }
        data.fiscalId = (document.getElementById("fiscalId") as HTMLInputElement).value.ifEmpty { null }
        data.bankName = (document.getElementById("bankName") as HTMLInputElement).value.ifEmpty { null }
        data.accountHolderName = (document.getElementById("accountHolderName") as HTMLInputElement).value.ifEmpty { null }
        data.accountNumber = (document.getElementById("accountNumber") as HTMLInputElement).value.ifEmpty { null }
        data.iban = (document.getElementById("iban") as HTMLInputElement).value.ifEmpty { null }
        data.swiftBic = (document.getElementById("swiftBic") as HTMLInputElement).value.ifEmpty { null }
        data.currency = (document.getElementById("currency") as HTMLSelectElement).value
        data.description = (document.getElementById("description") as HTMLTextAreaElement).value.ifEmpty { null }
        return data
    }

    private fun clearForm() {
        (document.getElementById("shelter-form") as? HTMLFormElement)?.reset()
        (document.getElementById("shelter-id") as? HTMLInputElement)?.value = ""
        document.getElementById("submit-btn")?.textContent = I18n.t("addShelter")
        (document.getElementById("cancel-btn"))?.asDynamic()?.style?.display = "none"
        editingId = null
    }

    private fun editShelter(id: Int) {
        window.asDynamic().fetch("/api/shelters/$id").then { output: dynamic ->
            if (output.ok != true) throw js("new Error('Failed to load shelter')")
            output.json().then { shelter: dynamic ->
                editingId = id
                (document.getElementById("shelter-id") as HTMLInputElement).value = id.toString()
                (document.getElementById("name") as HTMLInputElement).value = shelter.name.toString()
                (document.getElementById("country") as HTMLSelectElement).value = shelter.country.toString()
                (document.getElementById("state") as HTMLInputElement).value = shelter.state?.toString() ?: ""
                (document.getElementById("city") as HTMLInputElement).value = shelter.city.toString()
                (document.getElementById("address") as HTMLInputElement).value = shelter.address.toString()
                (document.getElementById("zip") as HTMLInputElement).value = shelter.zip?.toString() ?: ""
                (document.getElementById("phone") as HTMLInputElement).value = shelter.phone?.toString() ?: ""
                (document.getElementById("email") as HTMLInputElement).value = shelter.email?.toString() ?: ""
                (document.getElementById("website") as HTMLInputElement).value = shelter.website?.toString() ?: ""
                (document.getElementById("fiscalId") as HTMLInputElement).value = shelter.fiscalId?.toString() ?: ""
                (document.getElementById("bankName") as HTMLInputElement).value = shelter.bankName?.toString() ?: ""
                (document.getElementById("accountHolderName") as HTMLInputElement).value = shelter.accountHolderName?.toString() ?: ""
                (document.getElementById("accountNumber") as HTMLInputElement).value = shelter.accountNumber?.toString() ?: ""
                (document.getElementById("iban") as HTMLInputElement).value = shelter.iban?.toString() ?: ""
                (document.getElementById("swiftBic") as HTMLInputElement).value = shelter.swiftBic?.toString() ?: ""
                (document.getElementById("currency") as HTMLSelectElement).value = shelter.currency?.toString() ?: "USD"
                (document.getElementById("description") as HTMLTextAreaElement).value = shelter.description?.toString() ?: ""

                document.getElementById("submit-btn")?.textContent = I18n.t("updateShelter")
                (document.getElementById("cancel-btn"))?.asDynamic()?.style?.display = "inline-block"
                document.getElementById("shelter-form")?.asDynamic()?.scrollIntoView(js("({behavior: 'smooth'})"))
            }
        }.catch { _: dynamic -> showMessage(I18n.t("errorLoadingShelter"), "error") }
    }

    private fun deleteShelter(id: Int) {
        if (!window.confirm(I18n.t("confirmDeleteShelter"))) return
        window.asDynamic().fetch("/api/admin/shelters/$id", js("({method: 'DELETE'})")).then { output: dynamic ->
            if (output.ok != true) throw js("new Error('Failed to delete shelter')")
            showMessage(I18n.t("shelterDeleted"), "success")
            loadShelters()
        }.catch { _: dynamic -> showMessage(I18n.t("errorDeletingShelter"), "error") }
    }

    private fun onSubmit(e: Event) {
        e.preventDefault()
        val data = getFormData()
        val id = (document.getElementById("shelter-id") as HTMLInputElement).value
        val method = if (id.isNotEmpty()) "PUT" else "POST"
        val url = if (id.isNotEmpty()) "/api/admin/shelters/$id" else "/api/admin/shelters"

        window.asDynamic().fetch(url, js("({method: method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)})")).then { output: dynamic ->
            if (output.ok != true) {
                output.json().then { error: dynamic ->
                    showMessage(error.message?.toString() ?: I18n.t("errorSavingShelter"), "error")
                }
                return@then
            }
            showMessage(if (id.isNotEmpty()) I18n.t("shelterUpdated") else I18n.t("shelterAdded"), "success")
            clearForm()
            loadShelters()
        }.catch { _: dynamic -> showMessage(I18n.t("errorSavingShelter"), "error") }
    }
}
