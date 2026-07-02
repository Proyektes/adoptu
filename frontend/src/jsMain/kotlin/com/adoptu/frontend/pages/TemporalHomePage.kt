package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

@JsExport
@JsName("TemporalHomeSearchPage")
object TemporalHomeSearchPageModule {
    fun init() {
        window.asDynamic().searchTemporalHomes = { search() }
        document.getElementById("search-btn")?.addEventListener("click", { search() })
        val debounced = CommonModule.debounce(500) { search() }
        document.getElementById("search-state")?.addEventListener("input", { debounced() })
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
            container?.innerHTML = "<p data-i18n=\"noTemporalHomes\">No temporal homes found.</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { home ->
            val alias = home.alias?.toString()?.takeIf { it.isNotEmpty() } ?: "Temporal Home"
            "<div class=\"temporal-home-card\"><h3>$alias</h3><p>${home.city}, ${home.state}, ${I18n.translateCountry(home.country?.toString())}</p>" +
                "<a href=\"/temporal-home/${home.id}\" data-i18n=\"viewDetails\">View Details</a></div>"
        }
    }
}

@JsExport
@JsName("TemporalHomeProfilePage")
object TemporalHomeProfilePageModule {
    fun init() {
        window.asDynamic().blockRescuer = { rescuerId: Int -> blockRescuer(rescuerId) }
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
                    "<button class=\"btn btn-small\" onclick=\"blockRescuer(${r.rescuerId})\">Block Rescuer</button></div>"
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
        window.asDynamic().blockRescuerAndRedirect = { thId: Int, rId: Int -> blockRescuerAndRedirect(thId, rId) }
    }

    private fun blockRescuerAndRedirect(temporalHomeId: Int, rescuerId: Int) {
        window.asDynamic().fetch("/api/temporal-homes/block/$temporalHomeId?rescuer=$rescuerId").then { res: dynamic ->
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
