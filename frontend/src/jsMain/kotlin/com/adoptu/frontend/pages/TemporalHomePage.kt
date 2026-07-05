package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("TemporalHomeSearchPage")
object TemporalHomeSearchPageModule {
    fun init() {
        window.asDynamic().searchTemporalHomes = { search() }
        document.getElementById("search-btn")?.addEventListener("click", { search() })
        val debounced = CommonModule.debounce(500) { search() }
        listOf("search-state", "search-city", "search-zip", "search-neighborhood").forEach { id ->
            document.getElementById(id)?.addEventListener("input", { debounced() })
        }

        CommonModule.initCountrySelect("search-country") { window.asDynamic().onCountryChange() }
    }

    private fun search() {
        val params = CommonModule.buildLocationSearchParams()
        val query = params?.toString() ?: ""
        window.asDynamic().fetch("/api/temporal-homes?$query").then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Search failed')")
            res.json().then { homes: dynamic -> displayResults(homes) }
        }.catch { _: dynamic ->
            document.getElementById("results-container")?.innerHTML = "<p>Error loading results.</p>"
        }
    }

    private fun displayResults(homes: dynamic) {
        val container = document.getElementById("results-container").unsafeCast<HTMLElement?>()
        val list = homes as? Array<dynamic>
        if (list == null || list.isEmpty()) {
            container?.innerHTML = "<p>${I18n.t("noTemporalHomes")}</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { home ->
            val alias = home.alias?.toString()?.takeIf { it.isNotEmpty() } ?: "Temporal Home"
            val location = listOfNotNull(
                home.city?.toString()?.takeIf { it.isNotEmpty() },
                home.state?.toString()?.takeIf { it.isNotEmpty() },
                I18n.translateCountry(home.country?.toString())
            ).joinToString(", ")
            "<div class=\"temporal-home-card\">" +
                "<div class=\"temporal-home-icon\">🏠</div>" +
                "<div class=\"temporal-home-info\"><h3>$alias</h3><p class=\"location\">$location</p></div>" +
                "<a class=\"btn btn-small\" href=\"/temporal-home/${home.userId}\">${I18n.t("viewDetails")}</a></div>"
        }
    }
}

@JsExport
@JsName("TemporalHomeDetailPage")
object TemporalHomeDetailPageModule {
    private var temporalHomeId: Int = 0

    fun init() {
        val segments = window.location.pathname.split("/")
        val id = segments.lastOrNull { it.isNotEmpty() }
        if (id == null) {
            window.location.href = "/temporal-homes"
            return
        }
        temporalHomeId = id.toIntOrNull() ?: run {
            window.location.href = "/temporal-homes"
            return
        }

        ApiClientModule.getTemporalHomeById(id).then<Unit> { home ->
            ApiClientModule.me().then<Unit> { user -> render(home, user) }.catch { render(home, js("({authenticated: false})")) }
        }.catch {
            val container = document.getElementById("temporal-home-detail").unsafeCast<HTMLElement?>()
            container?.innerHTML = "<p>${I18n.t("temporalHomeNotFound")}</p><a href=\"/temporal-homes\">${I18n.t("backToSearch")}</a>"
        }
    }

    private fun render(home: dynamic, user: dynamic) {
        val container = document.getElementById("temporal-home-detail").unsafeCast<HTMLElement?>()
        val alias = home.alias?.toString()?.takeIf { it.isNotEmpty() } ?: "Temporal Home"
        val location = listOfNotNull(
            home.city?.toString()?.takeIf { it.isNotEmpty() },
            home.state?.toString()?.takeIf { it.isNotEmpty() },
            I18n.translateCountry(home.country?.toString())
        ).joinToString(", ")
        val memberSince = js("new Date(home.createdAt)").toLocaleDateString()

        val roles = user.activeRoles as? Array<String>
        val isRescuer = user.authenticated != false && (roles?.contains("RESCUER") == true || roles?.contains("ADMIN") == true)

        val sb = StringBuilder()
        sb.append("<div class=\"temporal-home-detail-header\">")
        sb.append("<div class=\"temporal-home-detail-icon\">🏠</div>")
        sb.append("<h1>$alias</h1>")
        if (location.isNotEmpty()) sb.append("<p class=\"location\">$location</p>")
        sb.append("<p class=\"member-since\">${I18n.t("memberSince")} $memberSince</p>")
        sb.append("</div>")

        sb.append("<div class=\"temporal-home-detail-body\">")
        sb.append("<h2>${I18n.t("contactThisHome")}</h2>")
        if (isRescuer) {
            sb.append(
                "<form id=\"contact-form\">" +
                    "<textarea id=\"contact-message\" rows=\"4\" placeholder=\"${I18n.t("yourMessage")}\"></textarea>" +
                    "<button type=\"submit\" class=\"btn\">${I18n.t("sendRequestBtn")}</button></form>"
            )
        } else {
            sb.append("<p>${I18n.t("loginAsRescuerToContact")}</p>")
        }
        sb.append("</div>")

        container?.innerHTML = sb.toString()

        val form = document.getElementById("contact-form")
        form?.addEventListener("submit", { e: Event ->
            e.preventDefault()
            val message = (document.getElementById("contact-message") as? HTMLTextAreaElement)?.value ?: ""
            ApiClientModule.sendTemporalHomeRequest(temporalHomeId, message).then<Unit> {
                (document.getElementById("message") as? HTMLElement)?.let {
                    it.className = "message success"
                    it.textContent = I18n.t("requestSentToHome")
                }
                form.unsafeCast<HTMLElement>().style.display = "none"
            }.catch { err: dynamic ->
                (document.getElementById("message") as? HTMLElement)?.let {
                    it.className = "message error"
                    it.textContent = err?.message?.toString() ?: "Error"
                }
            }
        })
    }
}

@JsExport
@JsName("TemporalHomeProfilePage")
object TemporalHomeProfilePageModule {
    fun init() {
        window.asDynamic().blockRescuer = { rescuerId: dynamic -> blockRescuer(rescuerId.toString().toInt()) }
        ApiClientModule.me().then<Unit> { user ->
            val roles = user.activeRoles as? Array<String>
            if (user.authenticated == false) {
                window.location.href = "/login"
                return@then
            }
            if (roles?.contains("TEMPORAL_HOME") != true && roles?.contains("ADMIN") != true) {
                window.location.href = "/"
                return@then
            }
            loadRequests()
        }.catch { window.location.href = "/login" }
    }

    private fun loadRequests() {
        val container = document.getElementById("requests-container").unsafeCast<HTMLElement?>()
        ApiClientModule.getTemporalHomeRequests().then<Unit> { requests ->
            val list = requests as? Array<dynamic>
            if (list == null || list.isEmpty()) {
                container?.innerHTML = "<p>No requests yet.</p>"
                return@then
            }
            container?.innerHTML = list.joinToString("") { r ->
                val petName = r.petName?.toString()?.takeIf { it.isNotEmpty() } ?: "a pet"
                "<div class=\"request-card\"><p><strong>${r.rescuerName}</strong> wants help with $petName</p><p>${r.message}</p>" +
                    "<button class=\"btn btn-small\" data-action=\"blockRescuer\" data-arg=\"${r.rescuerId}\">Block Rescuer</button></div>"
            }
        }.catch { err: dynamic -> console.error(err) }
    }

    private fun blockRescuer(rescuerId: Int) {
        if (!window.confirm("Block this rescuer from sending you more requests?")) return
        ApiClientModule.blockRescuer(rescuerId).then<Unit> { result ->
            window.alert(if (result.blocked == true) "Rescuer blocked!" else "Already blocked")
            loadRequests()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }
}

@JsExport
@JsName("TemporalHomeBlockPage")
object TemporalHomeBlockPageModule {
    fun init() {
        window.asDynamic().blockRescuerAndRedirect = { token: String -> blockRescuerAndRedirect(token) }
    }

    private fun blockRescuerAndRedirect(token: String) {
        window.asDynamic().fetch("/api/temporal-homes/block?token=$token").then { res: dynamic ->
            res.json().then { data: dynamic ->
                document.body?.innerHTML = if (data.blocked == true) {
                    "<h1>Rescuer blocked!</h1><p>You will no longer receive requests from this rescuer.</p><a href=\"/\">Go to Home</a>"
                } else {
                    "<h1>This rescuer was already blocked.</h1><a href=\"/\">Go to Home</a>"
                }
            }
        }.catch { _: dynamic ->
            document.body?.innerHTML = "<h1>Error blocking rescuer</h1>"
        }
    }
}
