package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.apiFetch
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.js.json

@JsExport
@JsName("ReportLostFoundPage")
object ReportLostFoundPageModule {
    private var latitude: Double? = null
    private var longitude: Double? = null

    fun init() {
        val emailRow = document.getElementById("reporter-contact-row") as? HTMLElement
        val phoneRow = document.getElementById("reporter-phone-row") as? HTMLElement
        val captchaRow = document.getElementById("captcha-row") as? HTMLElement

        ApiClientModule.me().then<Unit> { result: dynamic ->
            val authenticated = result.authenticated == true
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
        val kind = (document.querySelector("input[name=kind]:checked") as? HTMLInputElement)?.value ?: "LOST"
        val petType = (document.getElementById("pet-type") as? HTMLInputElement)?.value
        val description = (document.getElementById("description") as? HTMLTextAreaElement)?.value ?: ""
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
        if (country.isNullOrBlank()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("locationRequired")
            return
        }

        msg?.textContent = I18n.t("submittingEllipsis")
        msg?.className = ""

        val body = json(
            "kind" to kind,
            "petType" to petType,
            "description" to description,
            "reporterEmail" to reporterEmail,
            "reporterPhone" to reporterPhone,
            "captchaToken" to captchaToken,
            "latitude" to latitude,
            "longitude" to longitude,
            "country" to country,
            "state" to state,
            "city" to city
        )

        apiFetch("/api/lost-found/reports", json("method" to "POST", "body" to JSON.stringify(body)))
            .then<Unit> { _: dynamic ->
                msg?.className = "message success"
                msg?.textContent = I18n.t("lostFoundReportSubmitted")
            }
            .catch<Unit> { err: dynamic ->
                msg?.className = "message error"
                msg?.textContent = err?.message?.toString() ?: I18n.t("reportSubmitFailed")
            }
    }
}

@JsExport
@JsName("LostFoundBrowsePage")
object LostFoundBrowsePageModule {
    fun init() {
        document.getElementById("search-btn")?.addEventListener("click", { search() })
    }

    private fun search() {
        val errorDiv = document.getElementById("lost-found-error") as? HTMLElement
        val container = document.getElementById("lost-found-results") as? HTMLElement
        errorDiv?.style?.display = "none"
        container?.innerHTML = "<p>${I18n.t("loading")}</p>"

        val kind = (document.getElementById("browse-kind") as? HTMLSelectElement)?.value ?: "LOST"
        val params = CommonModule.buildLocationSearchParams()
        if (params == null) {
            errorDiv?.style?.display = "block"
            errorDiv?.textContent = I18n.t("countryRequired")
            container?.innerHTML = ""
            return
        }
        params.set("kind", kind)

        apiFetch("/api/lost-found/reports?${params.toString()}").then<Unit> { results: dynamic ->
            val list = results.unsafeCast<Array<dynamic>>()
            if (list.isEmpty()) {
                container?.innerHTML = "<p>${I18n.t("noLostFoundReportsFound")}</p>"
                return@then
            }
            container?.innerHTML = list.joinToString("") { report -> renderCard(report) }
        }.catch<Unit> {
            errorDiv?.style?.display = "block"
            errorDiv?.textContent = I18n.t("errorLoadingReports")
            container?.innerHTML = ""
        }
    }

    private fun renderCard(report: dynamic): String {
        val location = CommonModule.escapeHtml(report.locationLabel?.toString())
        val description = CommonModule.escapeHtml(report.description?.toString())
        val petType = report.petType?.toString()?.takeIf { it.isNotEmpty() }
        return """
            <div class="card-bg profile-section">
                <h3>${petType?.let { CommonModule.escapeHtml(it) } ?: I18n.t("petType")}</h3>
                <p>$location</p>
                <p>$description</p>
                <a class="btn" href="/lost-found/${report.id}">${I18n.t("viewDetails")}</a>
            </div>
        """.trimIndent()
    }
}

@JsExport
@JsName("LostFoundDetailPage")
object LostFoundDetailPageModule {
    private var currentReport: dynamic = null
    private var reportId: String = ""

    fun init() {
        val segments = window.location.pathname.split("/")
        val id = segments.lastOrNull { it.isNotEmpty() }
        if (id == null) {
            window.location.href = "/lost-found"
            return
        }
        reportId = id

        apiFetch("/api/lost-found/reports/$id").then<Unit> { report: dynamic ->
            currentReport = report
            render()
        }.catch<Unit> { window.location.href = "/lost-found" }
    }

    private fun render() {
        val report = currentReport ?: return
        val container = document.getElementById("lost-found-detail") ?: return

        val kindLabel = if (report.kind.toString() == "LOST") I18n.t("kindLost") else I18n.t("kindFound")
        val petTypeText = report.petType?.toString()
        val petTypeSuffix = if (!petTypeText.isNullOrEmpty()) " - " + CommonModule.escapeHtml(petTypeText) else ""
        container.innerHTML = """
            <h1>$kindLabel$petTypeSuffix</h1>
            <p>${CommonModule.escapeHtml(report.locationLabel?.toString())}</p>
            <p>${CommonModule.escapeHtml(report.description?.toString())}</p>
            <div class="form-row">
                <label for="contact-email">${I18n.t("yourEmail")}</label>
                <input type="email" id="contact-email">
            </div>
            <div class="form-row">
                <label for="contact-message">${I18n.t("message")}</label>
                <textarea id="contact-message"></textarea>
            </div>
            <button type="button" class="btn" id="contact-btn">${I18n.t("contactReporter")}</button>
        """.trimIndent()

        document.getElementById("contact-btn")?.addEventListener("click", { sendContact() })
    }

    private fun sendContact() {
        val msg = document.getElementById("message")
        val fromEmail = (document.getElementById("contact-email") as? org.w3c.dom.HTMLInputElement)?.value ?: ""
        val message = (document.getElementById("contact-message") as? HTMLTextAreaElement)?.value ?: ""
        if (fromEmail.isBlank() || message.isBlank()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("contactFormIncomplete")
            return
        }
        val body = json("fromEmail" to fromEmail, "message" to message)
        apiFetch("/api/lost-found/reports/$reportId/contact", json("method" to "POST", "body" to JSON.stringify(body)))
            .then<Unit> { _: dynamic ->
                msg?.className = "message success"
                msg?.textContent = I18n.t("contactSent")
            }
            .catch<Unit> { err: dynamic ->
                msg?.className = "message error"
                msg?.textContent = err?.message?.toString() ?: I18n.t("contactFailed")
            }
    }
}

@JsExport
@JsName("LostFoundResolvePage")
object LostFoundResolvePageModule {
    fun init() {
        val params = js("new URLSearchParams(window.location.search)")
        val token = params.get("token") as? String
        if (token.isNullOrBlank()) {
            showError()
            return
        }
        apiFetch("/api/lost-found/resolve?token=" + window.asDynamic().encodeURIComponent(token))
            .then<Unit> { _: dynamic -> showSuccess() }
            .catch<Unit> { showError() }
    }

    private fun showSuccess() {
        document.getElementById("resolve-success")?.className = "verification-success"
        document.getElementById("resolve-error")?.className = "verification-error hidden"
    }

    private fun showError() {
        document.getElementById("resolve-error")?.className = "verification-error"
        document.getElementById("resolve-success")?.className = "verification-success hidden"
    }
}
