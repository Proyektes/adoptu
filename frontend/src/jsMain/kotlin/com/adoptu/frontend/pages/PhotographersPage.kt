package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("PhotographersPage")
object PhotographersPageModule {
    // PhotographerService rejects more than this many photographers per request.
    private const val MAX_SELECTED = 3

    private var isAuthenticated = false

    // Photographers ticked for a combined request: userId -> (displayName, fee label).
    private val selected = LinkedHashMap<Int, Pair<String, String>>()

    fun init() {
        window.asDynamic().searchPhotographers = { search() }
        createRequestModal()
        createSelectionBar()
        ApiClientModule.me().then<Unit> { result: dynamic -> isAuthenticated = result.authenticated == true }.catch<Unit> { }

        document.getElementById("search-btn")?.addEventListener("click", { search() })
        val debounced = CommonModule.debounce(500) { search() }
        listOf("search-state", "search-city", "search-zip", "search-neighborhood").forEach { id ->
            document.getElementById(id)?.addEventListener("input", { debounced() })
        }

        CommonModule.initCountrySelect("search-country") { window.asDynamic().onCountryChange() }
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
            container?.innerHTML = "<p data-i18n=\"noPhotographersAvailable\">${I18n.t("noPhotographersAvailable")}</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { p ->
            val fee = if (p.photographerFee != null) "${p.photographerFee} ${p.photographerCurrency ?: "USD"}" else "Free"
            val location = listOfNotNull(
                p.photographerCity?.toString()?.takeIf { it.isNotEmpty() },
                p.photographerState?.toString()?.takeIf { it.isNotEmpty() },
                p.photographerCountry?.toString()?.takeIf { it.isNotEmpty() }
            ).joinToString(", ")
            val id = p.userId.toString().toInt()
            val name = CommonModule.escapeHtml(p.displayName?.toString())
            val checked = if (selected.containsKey(id)) " checked" else ""
            "<div class=\"photographer-card\"><div class=\"photographer-info\"><h3>$name</h3>" +
                (if (location.isNotEmpty()) "<p class=\"photographer-location\">${CommonModule.escapeHtml(location)}</p>" else "") +
                "<p class=\"photographer-fee\">${I18n.t("sessionFee")}: <strong>$fee</strong></p></div>" +
                "<button class=\"btn request-btn\" data-id=\"$id\" data-name=\"$name\" data-fee=\"$fee\">${I18n.t("requestPhotoSession")}</button>" +
                "<label class=\"photographer-select\"><input type=\"checkbox\" class=\"select-photographer\" data-id=\"$id\" data-name=\"$name\" data-fee=\"$fee\"$checked>" +
                "<span>${I18n.t("selectPhotographer")}</span></label></div>"
        }

        document.querySelectorAll(".request-btn").forEachElement { node ->
            val btn = node.unsafeCast<HTMLElement>()
            btn.addEventListener("click", {
                if (!isAuthenticated) {
                    window.location.href = CommonModule.loginUrlWithReturn()
                    return@addEventListener
                }
                val id = btn.asDynamic().dataset.id.toString().toInt()
                val name = btn.asDynamic().dataset.name.toString()
                val fee = btn.asDynamic().dataset.fee.toString()
                showRequestModal(id, name, fee)
            })
        }

        document.querySelectorAll(".select-photographer").forEachElement { node ->
            val box = node.unsafeCast<HTMLInputElement>()
            box.addEventListener("change", {
                if (!isAuthenticated) {
                    box.checked = false
                    window.location.href = CommonModule.loginUrlWithReturn()
                    return@addEventListener
                }
                val id = box.asDynamic().dataset.id.toString().toInt()
                if (box.checked) {
                    if (selected.size >= MAX_SELECTED) {
                        box.checked = false
                        return@addEventListener
                    }
                    selected[id] = box.asDynamic().dataset.name.toString() to box.asDynamic().dataset.fee.toString()
                } else {
                    selected.remove(id)
                }
                updateSelectionBar()
            })
        }
        updateSelectionBar()
    }

    // --- Multi-select bar --------------------------------------------------------------------

    private fun createSelectionBar() {
        val bar = document.createElement("div").unsafeCast<HTMLElement>()
        bar.id = "multi-request-bar"
        bar.className = "multi-request-bar hidden"
        bar.innerHTML = """
            <span id="selected-count"></span>
            <button type="button" class="btn" id="request-selected">${I18n.t("requestSelectedPhotographers")}</button>
            <button type="button" class="btn" id="clear-selection">${I18n.t("clearSelection")}</button>
        """.trimIndent()
        document.body?.appendChild(bar)

        document.getElementById("request-selected")?.addEventListener("click", {
            if (selected.isNotEmpty()) showMultiRequestModal()
        })
        document.getElementById("clear-selection")?.addEventListener("click", { clearSelection() })
    }

    private fun updateSelectionBar() {
        val bar = document.getElementById("multi-request-bar") ?: return
        val count = selected.size
        document.getElementById("selected-count")?.textContent =
            I18n.t("photographersSelected").replace("{count}", "$count/$MAX_SELECTED")
        bar.classList.toggle("hidden", count == 0)
        // Once the cap is reached, grey out the remaining boxes instead of silently refusing.
        val full = count >= MAX_SELECTED
        document.querySelectorAll(".select-photographer").forEachElement { node ->
            val box = node.unsafeCast<HTMLInputElement>()
            box.disabled = full && !box.checked
        }
    }

    private fun clearSelection() {
        selected.clear()
        document.querySelectorAll(".select-photographer").forEachElement { node ->
            node.unsafeCast<HTMLInputElement>().checked = false
        }
        updateSelectionBar()
    }

    // --- Request modal (single photographer or the current selection) ------------------------

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
                        <button type="submit" class="btn btn-primary" id="submit-request">${I18n.t("sendRequest")}</button>
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
            val message = (document.getElementById("request-message") as? HTMLTextAreaElement)?.value?.trim() ?: ""
            val submit = document.getElementById("submit-request") as? HTMLButtonElement
            val ids = modal.asDynamic().dataset.photographerIds.toString()
                .split(",").filter { it.isNotBlank() }.map { it.toInt() }
            if (ids.size > 1 && message.isEmpty()) {
                // The multi endpoint requires a message; the single one accepts an empty note.
                window.alert(I18n.t("messageRequired"))
                return@addEventListener
            }
            submit?.disabled = true
            val call = if (ids.size == 1) {
                ApiClientModule.createPhotographyRequest(ids[0], null, message)
            } else {
                ApiClientModule.createMultiPhotographyRequest(ids.toTypedArray(), null, message)
            }
            call.then<Unit> {
                modal.style.display = "none"
                window.alert(I18n.t(if (ids.size == 1) "requestSentSuccessfully" else "requestsSentSuccessfully"))
                if (ids.size > 1) clearSelection()
            }.catch { err: dynamic ->
                window.alert("Error: ${err?.message ?: err}")
            }.then<Unit> { submit?.disabled = false }
        })
    }

    private fun showRequestModal(photographerId: Int, photographerName: String, fee: String) {
        val modal = document.getElementById("request-modal").unsafeCast<HTMLElement>()
        document.getElementById("modal-title")?.textContent = I18n.t("requestPhotoSession")
        document.getElementById("modal-photographer")?.innerHTML =
            "${I18n.t("requestTo")}: <strong>${CommonModule.escapeHtml(photographerName)}</strong><br>${I18n.t("sessionFee")}: <strong>$fee</strong>"
        val messageInput = document.getElementById("request-message") as? HTMLTextAreaElement
        messageInput?.placeholder = I18n.t("enterMessage").replace("{name}", photographerName)
        messageInput?.value = ""
        modal.asDynamic().dataset.photographerIds = photographerId.toString()
        modal.style.display = "flex"
    }

    private fun showMultiRequestModal() {
        val modal = document.getElementById("request-modal").unsafeCast<HTMLElement>()
        document.getElementById("modal-title")?.textContent = I18n.t("requestPhotoSession")
        val names = selected.values.joinToString("") { (name, fee) ->
            "<li><strong>${CommonModule.escapeHtml(name)}</strong> · ${I18n.t("sessionFee")}: $fee</li>"
        }
        document.getElementById("modal-photographer")?.innerHTML = "${I18n.t("requestToMultiple")}:<ul class=\"selected-photographers\">$names</ul>"
        val messageInput = document.getElementById("request-message") as? HTMLTextAreaElement
        messageInput?.placeholder = I18n.t("enterMessage").replace("{name}", selected.values.joinToString(", ") { it.first })
        messageInput?.value = ""
        modal.asDynamic().dataset.photographerIds = selected.keys.joinToString(",")
        modal.style.display = "flex"
    }
}
