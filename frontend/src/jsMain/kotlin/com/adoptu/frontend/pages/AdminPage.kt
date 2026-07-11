package com.adoptu.frontend.pages

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
        window.asDynamic().banUser = { id: dynamic, name: dynamic -> showBanModal(id.toString().toInt(), name.toString()) }
        window.asDynamic().unbanUser = { id: dynamic -> unbanUser(id.toString().toInt()) }
        window.asDynamic().resetPassword = { id: dynamic, email: dynamic -> resetPassword(id.toString().toInt(), email.toString()) }

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
        container?.innerHTML = "<div class=\"admin-table-wrap\"><table class=\"admin-table\"><thead><tr><th>Email</th><th>Name</th><th>Roles</th><th>Status</th><th>Actions</th></tr></thead><tbody>" +
            list.joinToString("") { u ->
                // Each role as its own pill instead of a comma-joined string; ADMIN gets the
                // accent color so admins stand out at a glance in a long user list.
                val roles = (u.activeRoles as? Array<dynamic>)?.joinToString(" ") { role ->
                    val roleStr = role.toString()
                    val badgeClass = if (roleStr == "ADMIN") "badge badge-role badge-role-admin" else "badge badge-role"
                    "<span class=\"$badgeClass\">$roleStr</span>"
                } ?: ""
                val isBanned = u.isBanned == true
                val name = CommonModule.escapeHtml(u.displayName?.toString() ?: "")
                val email = CommonModule.escapeHtml(u.email?.toString() ?: "")
                val statusBadge = if (isBanned) "<span class=\"status-banned\">Banned</span>" else "<span class=\"status-active\">Active</span>"
                val banAction = if (isBanned) {
                    "<button class=\"btn btn-secondary btn-small\" data-action=\"unbanUser\" data-arg=\"${u.id}\">Unban</button>"
                } else {
                    "<button class=\"btn btn-danger btn-small\" data-action=\"banUser\" data-arg=\"${u.id}\" data-arg2=\"$email\">Ban</button>"
                }
                val resetAction = "<button class=\"btn btn-secondary btn-small\" data-action=\"resetPassword\" data-arg=\"${u.id}\" data-arg2=\"$email\">Reset Password</button>"
                "<tr><td>$email</td><td>$name</td><td>$roles</td><td>$statusBadge</td><td>$banAction $resetAction</td></tr>"
            } + "</tbody></table></div>"
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

    // Backend invalidates the target's password/passkeys and re-sends the forgot-password
    // email (POST /api/admin/users/{id}/reset-password) - the confirm text mirrors exactly
    // what that endpoint does so an admin can't trigger it by accident.
    private fun resetPassword(id: Int, email: String) {
        if (!window.confirm("This will invalidate $email's current password and passkeys and email them a reset link. Continue?")) return
        window.asDynamic().fetch("/api/admin/users/$id/reset-password", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to reset password')")
            window.alert("Password reset email sent to $email.")
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to reset password") }
    }

}
