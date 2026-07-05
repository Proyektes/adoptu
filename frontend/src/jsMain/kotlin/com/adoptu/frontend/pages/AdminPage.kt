package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("AdminPage")
object AdminPageModule {
    private var banUserId: Int? = null

    fun init() {
        window.asDynamic().confirmBan = { confirmBan() }
        window.asDynamic().hideBanModal = { hideBanModal() }
        window.asDynamic().banUser = { id: Int, name: String -> showBanModal(id, name) }
        window.asDynamic().unbanUser = { id: Int -> unbanUser(id) }
        window.asDynamic().deletePet = { id: Int -> deletePet(id) }

        document.getElementById("tab-users")?.addEventListener("click", { switchTab("users") })
        document.getElementById("tab-pets")?.addEventListener("click", { switchTab("pets") })

        loadUsers()
    }

    private fun switchTab(tab: String) {
        val usersTab = document.getElementById("users-tab").unsafeCast<HTMLElement?>()
        val petsTab = document.getElementById("pets-tab").unsafeCast<HTMLElement?>()
        val usersBtn = document.getElementById("tab-users").unsafeCast<HTMLElement?>()
        val petsBtn = document.getElementById("tab-pets").unsafeCast<HTMLElement?>()

        if (tab == "users") {
            usersTab?.style?.display = "block"
            petsTab?.style?.display = "none"
            usersBtn?.classList?.add("active")
            petsBtn?.classList?.remove("active")
            loadUsers()
        } else {
            usersTab?.style?.display = "none"
            petsTab?.style?.display = "block"
            usersBtn?.classList?.remove("active")
            petsBtn?.classList?.add("active")
            loadPets()
        }
    }

    private fun loadUsers() {
        val container = document.getElementById("users-container").unsafeCast<HTMLElement?>()
        window.asDynamic().fetch("/api/admin/users", js("({credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to load users')")
            res.json().then { users: dynamic -> renderUsers(users, container) }
        }.catch { _: dynamic ->
            container?.innerHTML = "<p>Failed to load users.</p>"
        }
    }

    private fun renderUsers(data: dynamic, container: HTMLElement?) {
        val list = (data as? Array<dynamic>) ?: arrayOf()
        if (list.isEmpty()) {
            container?.innerHTML = "<p>No users found.</p>"
            return
        }
        container?.innerHTML = "<table class=\"admin-table\"><thead><tr><th>Email</th><th>Name</th><th>Roles</th><th>Status</th><th>Actions</th></tr></thead><tbody>" +
            list.joinToString("") { u ->
                val roles = (u.activeRoles as? Array<dynamic>)?.joinToString(", ") ?: ""
                val isBanned = u.banned == true
                val name = CommonModule.escapeHtml(u.displayName?.toString() ?: "")
                val email = CommonModule.escapeHtml(u.email?.toString() ?: "")
                val statusBadge = if (isBanned) "<span class=\"status-banned\">Banned</span>" else "<span class=\"status-active\">Active</span>"
                val action = if (isBanned) {
                    "<button class=\"btn btn-secondary\" onclick=\"unbanUser(${u.id})\">Unban</button>"
                } else {
                    "<button class=\"btn btn-danger\" onclick=\"banUser(${u.id}, '${email.replace("'", "\\'")}')\">Ban</button>"
                }
                "<tr><td>$email</td><td>$name</td><td>$roles</td><td>$statusBadge</td><td>$action</td></tr>"
            } + "</tbody></table>"
    }

    private fun showBanModal(id: Int, name: String) {
        banUserId = id
        document.getElementById("ban-user-name")?.textContent = name
        (document.getElementById("ban-reason") as? HTMLTextAreaElement)?.value = ""
        (document.getElementById("ban-modal") as? HTMLElement)?.style?.display = "flex"
    }

    private fun hideBanModal() {
        (document.getElementById("ban-modal") as? HTMLElement)?.style?.display = "none"
        banUserId = null
    }

    private fun confirmBan() {
        val id = banUserId ?: return
        val reason = (document.getElementById("ban-reason") as? HTMLTextAreaElement)?.value ?: ""
        val body = js("({reason: reason})")
        window.asDynamic().fetch(
            "/api/admin/users/$id/ban",
            js("({method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body), credentials: 'include'})")
        ).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to ban user')")
            hideBanModal()
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to ban user") }
    }

    private fun unbanUser(id: Int) {
        if (!window.confirm("Unban this user?")) return
        window.asDynamic().fetch("/api/admin/users/$id/unban", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to unban user')")
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to unban user") }
    }

    private fun loadPets() {
        val container = document.getElementById("pets").unsafeCast<HTMLElement?>()
        val msg = document.getElementById("message")
        ApiClientModule.getPets().then<Unit> { pets -> renderPets(pets, container) }
            .catch { _: dynamic ->
                msg?.className = "message error"
                msg?.textContent = "Failed to load pets."
            }
    }

    private fun renderPets(data: dynamic, container: HTMLElement?) {
        val list = (data as? Array<dynamic>) ?: arrayOf()
        if (list.isEmpty()) {
            container?.innerHTML = "<p>No pets found.</p>"
            return
        }
        container?.innerHTML = list.joinToString("") { p ->
            "<div class=\"pet-card\"><div class=\"pet-card-body\">" +
                "<h3>${CommonModule.escapeHtml(p.name?.toString())}</h3><p class=\"pet-status\">${p.status}</p>" +
                "<div class=\"pet-card-actions\"><a href=\"/pet/${p.id}\" class=\"btn\">View</a>" +
                "<button class=\"btn btn-danger\" onclick=\"deletePet(${p.id})\">Delete</button></div>" +
                "</div></div>"
        }
    }

    private fun deletePet(id: Int) {
        if (!window.confirm("Delete this pet?")) return
        ApiClientModule.deletePet(id.toString()).then<Unit> { loadPets() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to delete pet") }
    }
}
