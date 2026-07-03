package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("PhotographersPage")
object PhotographersPageModule {
    fun init() {
        window.asDynamic().searchPhotographers = { search() }
        createRequestModal()

        document.getElementById("search-btn")?.addEventListener("click", { search() })
        val debounced = CommonModule.debounce(500) { search() }
        listOf("search-state", "search-city", "search-zip", "search-neighborhood").forEach { id ->
            document.getElementById(id)?.addEventListener("input", { debounced() })
        }
    }

    private fun search() {
        val params = CommonModule.buildLocationSearchParams()
        val container = document.getElementById("photographers").unsafeCast<HTMLElement?>()
        if (params == null) {
            container?.innerHTML = "<p>${I18n.t("pleaseSelectCountry")}</p>"
            return
        }
        load("/api/photographers?" + params.toString())
    }

    private fun load(url: String) {
        val container = document.getElementById("photographers").unsafeCast<HTMLElement?>()
        window.asDynamic().fetch(url).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to load photographers')")
            res.json().then { data: dynamic -> render(data, container) }
        }.catch { _: dynamic ->
            container?.innerHTML = "<p>${I18n.t("errorLoadingPhotographers")}</p>"
        }
    }

    private fun render(data: dynamic, container: HTMLElement?) {
        val list = data as? Array<dynamic>
        if (list == null || list.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("noPhotographersAvailable")}</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { p ->
            val fee = if (p.photographerFee != null) "${p.photographerFee} ${p.photographerCurrency ?: "USD"}" else "Free"
            val location = listOfNotNull(
                p.photographerCity?.toString()?.takeIf { it.isNotEmpty() },
                p.photographerState?.toString()?.takeIf { it.isNotEmpty() },
                p.photographerCountry?.toString()?.takeIf { it.isNotEmpty() }
            ).joinToString(", ")
            "<div class=\"photographer-card\"><div class=\"photographer-info\"><h3>${p.displayName}</h3>" +
                (if (location.isNotEmpty()) "<p class=\"photographer-location\">$location</p>" else "") +
                "<p class=\"photographer-fee\">${I18n.t("sessionFee")}: <strong>$fee</strong></p></div>" +
                "<button class=\"btn request-btn\" data-id=\"${p.userId}\" data-name=\"${p.displayName}\" data-fee=\"$fee\">${I18n.t("requestPhotoSession")}</button></div>"
        }

        document.querySelectorAll(".request-btn").forEachElement { node ->
            val btn = node.unsafeCast<HTMLElement>()
            btn.addEventListener("click", {
                val id = btn.asDynamic().dataset.id.toString().toInt()
                val name = btn.asDynamic().dataset.name.toString()
                val fee = btn.asDynamic().dataset.fee.toString()
                showRequestModal(id, name, fee)
            })
        }
    }

    private fun createRequestModal() {
        val modal = document.createElement("div").unsafeCast<HTMLElement>()
        modal.id = "request-modal"
        modal.className = "form-modal"
        modal.style.display = "none"
        modal.innerHTML = """
            <div class="form-modal-content card-bg">
                <h2 id="modal-title">${I18n.t("requestPhotoSession")}</h2>
                <p id="modal-photographer" class="photographer-fee"></p>
                <form id="request-form">
                    <div class="form-group">
                        <label for="request-message">${I18n.t("message")}</label>
                        <textarea id="request-message" rows="4" class="form-control"></textarea>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">${I18n.t("sendRequest")}</button>
                        <button type="button" class="btn" id="cancel-request">${I18n.t("cancel")}</button>
                    </div>
                </form>
            </div>
        """.trimIndent()
        document.body?.appendChild(modal)

        document.getElementById("cancel-request")?.addEventListener("click", { modal.style.display = "none" })
        modal.addEventListener("click", { e: Event -> if (e.target == modal) modal.style.display = "none" })

        document.getElementById("request-form")?.addEventListener("submit", { e: Event ->
            e.preventDefault()
            val message = (document.getElementById("request-message") as? HTMLTextAreaElement)?.value ?: ""
            val photographerId = modal.asDynamic().dataset.photographerId.toString().toInt()
            ApiClientModule.createPhotographyRequest(photographerId, null, message).then<Unit> {
                modal.style.display = "none"
                window.alert(I18n.t("requestSentSuccessfully"))
            }.catch { err: dynamic ->
                window.alert("Error: ${err?.message ?: err}")
            }
        })
    }

    private fun showRequestModal(photographerId: Int, photographerName: String, fee: String) {
        val modal = document.getElementById("request-modal").unsafeCast<HTMLElement>()
        document.getElementById("modal-title")?.textContent = I18n.t("requestPhotoSession")
        document.getElementById("modal-photographer")?.innerHTML =
            "${I18n.t("requestTo")}: <strong>$photographerName</strong><br>${I18n.t("sessionFee")}: <strong>$fee</strong>"
        val messageInput = document.getElementById("request-message") as? HTMLTextAreaElement
        messageInput?.placeholder = I18n.t("enterMessage").replace("{name}", photographerName)
        messageInput?.value = ""
        modal.asDynamic().dataset.photographerId = photographerId.toString()
        modal.style.display = "flex"
    }
}
