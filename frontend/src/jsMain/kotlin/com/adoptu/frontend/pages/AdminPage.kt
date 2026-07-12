package com.adoptu.frontend.pages

import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("AdminPage")
object AdminPageModule {
    private var banUserId: Int? = null

    // Users tab filter/pagination state - kept here rather than re-reading the DOM on every
    // load, since page also changes via Prev/Next (no corresponding input to read from).
    private var userPage = 1
    private var userRole: String = ""
    private var userSearch: String = ""
    private var userShowInactive = false
    private var userShowBanned = false

    private var petPage = 1
    private var petSearch: String = ""
    private var petShowInactive = false

    fun init() {
        window.asDynamic().confirmBan = { confirmBan() }
        window.asDynamic().hideBanModal = { hideBanModal() }
        window.asDynamic().banUser = { id: dynamic, name: dynamic -> showBanModal(id.toString().toInt(), name.toString()) }
        window.asDynamic().unbanUser = { id: dynamic -> unbanUser(id.toString().toInt()) }
        window.asDynamic().resetPassword = { id: dynamic, email: dynamic -> resetPassword(id.toString().toInt(), email.toString()) }
        window.asDynamic().deactivateUser = { id: dynamic -> deactivateUser(id.toString().toInt()) }
        window.asDynamic().reactivateUser = { id: dynamic -> reactivateUser(id.toString().toInt()) }
        window.asDynamic().deactivatePet = { id: dynamic -> deactivatePet(id.toString().toInt()) }
        window.asDynamic().reactivatePet = { id: dynamic -> reactivatePet(id.toString().toInt()) }
        window.asDynamic().usersPrevPage = { if (userPage > 1) { userPage--; loadUsers() } }
        window.asDynamic().usersNextPage = { userPage++; loadUsers() }
        window.asDynamic().petsPrevPage = { if (petPage > 1) { petPage--; loadPetsAdmin() } }
        window.asDynamic().petsNextPage = { petPage++; loadPetsAdmin() }

        document.getElementById("tab-users")?.addEventListener("click", { switchTab("users") })
        document.getElementById("tab-pets")?.addEventListener("click", { switchTab("pets") })

        val onUserFilterChange = { userPage = 1; loadUsers() }
        document.getElementById("user-role-filter")?.addEventListener("change", {
            userRole = (document.getElementById("user-role-filter") as? HTMLSelectElement)?.value ?: ""
            onUserFilterChange()
        })
        document.getElementById("user-show-inactive")?.addEventListener("change", {
            userShowInactive = (document.getElementById("user-show-inactive") as? HTMLInputElement)?.checked ?: false
            onUserFilterChange()
        })
        document.getElementById("user-show-banned")?.addEventListener("change", {
            userShowBanned = (document.getElementById("user-show-banned") as? HTMLInputElement)?.checked ?: false
            onUserFilterChange()
        })
        val debouncedUserSearch = CommonModule.debounce(300) {
            userSearch = (document.getElementById("user-search") as? HTMLInputElement)?.value ?: ""
            onUserFilterChange()
        }
        document.getElementById("user-search")?.addEventListener("input", { debouncedUserSearch() })

        val onPetFilterChange = { petPage = 1; loadPetsAdmin() }
        document.getElementById("pet-show-inactive")?.addEventListener("change", {
            petShowInactive = (document.getElementById("pet-show-inactive") as? HTMLInputElement)?.checked ?: false
            onPetFilterChange()
        })
        val debouncedPetSearch = CommonModule.debounce(300) {
            petSearch = (document.getElementById("pet-search") as? HTMLInputElement)?.value ?: ""
            onPetFilterChange()
        }
        document.getElementById("pet-search")?.addEventListener("input", { debouncedPetSearch() })

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
            loadPetsAdmin()
        }
    }

    private fun formatDate(epochMillis: dynamic): String {
        val date = js("new Date(epochMillis)")
        return date.toLocaleDateString().unsafeCast<String>()
    }

    private fun buildQuery(params: Map<String, String>): String {
        val parts = params.filterValues { it.isNotEmpty() }.map { (k, v) -> "$k=" + window.asDynamic().encodeURIComponent(v) }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    private fun renderPagination(container: HTMLElement?, total: Int, page: Int, pageSize: Int, prevAction: String, nextAction: String) {
        val totalPages = if (pageSize <= 0) 1 else maxOf(1, (total + pageSize - 1) / pageSize)
        val prevDisabled = if (page <= 1) "disabled" else ""
        val nextDisabled = if (page >= totalPages) "disabled" else ""
        container?.innerHTML = """
            <button class="btn btn-secondary btn-small" data-action="$prevAction" $prevDisabled>Prev</button>
            <span>Page $page of $totalPages ($total total)</span>
            <button class="btn btn-secondary btn-small" data-action="$nextAction" $nextDisabled>Next</button>
        """.trimIndent()
    }

    private fun loadUsers() {
        val container = document.getElementById("users-container").unsafeCast<HTMLElement?>()
        val pagination = document.getElementById("users-pagination").unsafeCast<HTMLElement?>()
        val query = buildQuery(
            mapOf(
                "page" to userPage.toString(),
                "pageSize" to "20",
                "role" to userRole,
                "search" to userSearch,
                "includeInactive" to userShowInactive.toString(),
                "includeBanned" to userShowBanned.toString()
            )
        )
        window.asDynamic().fetch("/api/admin/users$query", js("({credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to load users')")
            res.json().then { page: dynamic ->
                renderUsers(page.items, container)
                renderPagination(pagination, (page.total as Int), (page.page as Int), (page.pageSize as Int), "usersPrevPage", "usersNextPage")
            }
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
                val isInactive = u.deactivatedAt != null
                val name = CommonModule.escapeHtml(u.displayName?.toString() ?: "")
                val email = CommonModule.escapeHtml(u.email?.toString() ?: "")
                val statusCell = buildString {
                    append(if (isBanned) "<span class=\"status-banned\">Banned</span>" else "<span class=\"status-active\">Active</span>")
                    if (isInactive) {
                        append(" <span class=\"status-inactive\">Inactive</span>")
                        append("<div class=\"admin-deactivated-note\">Deactivated ${formatDate(u.deactivatedAt)}</div>")
                    }
                }
                val banAction = if (isBanned) {
                    "<button class=\"btn btn-secondary btn-small\" data-action=\"unbanUser\" data-arg=\"${u.id}\">Unban</button>"
                } else {
                    "<button class=\"btn btn-danger btn-small\" data-action=\"banUser\" data-arg=\"${u.id}\" data-arg2=\"$email\">Ban</button>"
                }
                val deactivateAction = if (isInactive) {
                    "<button class=\"btn btn-secondary btn-small\" data-action=\"reactivateUser\" data-arg=\"${u.id}\">Reactivate</button>"
                } else {
                    "<button class=\"btn btn-secondary btn-small\" data-action=\"deactivateUser\" data-arg=\"${u.id}\">Deactivate</button>"
                }
                val resetAction = "<button class=\"btn btn-secondary btn-small\" data-action=\"resetPassword\" data-arg=\"${u.id}\" data-arg2=\"$email\">Reset Password</button>"
                "<tr><td>$email</td><td>$name</td><td>$roles</td><td>$statusCell</td><td>$banAction $deactivateAction $resetAction</td></tr>"
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
        if (!window.confirm(I18n.t("confirmUnbanUser"))) return
        window.asDynamic().fetch("/api/admin/users/$id/unban", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to unban user')")
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to unban user") }
    }

    // Backend invalidates the target's password/passkeys and re-sends the forgot-password
    // email (POST /api/admin/users/{id}/reset-password) - the confirm text mirrors exactly
    // what that endpoint does so an admin can't trigger it by accident.
    private fun resetPassword(id: Int, email: String) {
        if (!window.confirm(I18n.t("confirmResetUserCredentials").replace("{email}", email))) return
        window.asDynamic().fetch("/api/admin/users/$id/reset-password", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to reset password')")
            window.alert("Password reset email sent to $email.")
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to reset password") }
    }

    // Independent of Ban/Unban - see POST /api/admin/users/{id}/deactivate in UsersRoutes.kt.
    private fun deactivateUser(id: Int) {
        if (!window.confirm(I18n.t("confirmDeactivateUser"))) return
        window.asDynamic().fetch("/api/admin/users/$id/deactivate", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to deactivate user')")
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to deactivate user") }
    }

    private fun reactivateUser(id: Int) {
        window.asDynamic().fetch("/api/admin/users/$id/reactivate", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to reactivate user')")
            loadUsers()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to reactivate user") }
    }

    // Admin-only paginated/searchable pets overview - see adminPetsRoutes() in PetsRoutes.kt.
    // Separate from the "Manage Pets" link (/my-pets) kept above it for full add/edit.
    private fun loadPetsAdmin() {
        val container = document.getElementById("pets-admin-container").unsafeCast<HTMLElement?>()
        val pagination = document.getElementById("pets-pagination").unsafeCast<HTMLElement?>()
        val query = buildQuery(
            mapOf(
                "page" to petPage.toString(),
                "pageSize" to "20",
                "search" to petSearch,
                "includeInactive" to petShowInactive.toString()
            )
        )
        window.asDynamic().fetch("/api/admin/pets$query", js("({credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to load pets')")
            res.json().then { page: dynamic ->
                renderPetsAdmin(page.items, container)
                renderPagination(pagination, (page.total as Int), (page.page as Int), (page.pageSize as Int), "petsPrevPage", "petsNextPage")
            }
        }.catch { _: dynamic ->
            container?.innerHTML = "<p>Failed to load pets.</p>"
        }
    }

    private fun renderPetsAdmin(data: dynamic, container: HTMLElement?) {
        val list = (data as? Array<dynamic>) ?: arrayOf()
        if (list.isEmpty()) {
            container?.innerHTML = "<p>No pets found.</p>"
            return
        }
        container?.innerHTML = "<div class=\"admin-table-wrap\"><table class=\"admin-table\"><thead><tr><th>Name</th><th>Type</th><th>Status</th><th>Rescuer</th><th>Actions</th></tr></thead><tbody>" +
            list.joinToString("") { p ->
                val isInactive = p.deactivatedAt != null
                val name = CommonModule.escapeHtml(p.name?.toString() ?: "")
                val statusCell = buildString {
                    append("<span class=\"badge badge-role\">${p.status}</span>")
                    if (isInactive) {
                        append(" <span class=\"status-inactive\">Inactive</span>")
                        append("<div class=\"admin-deactivated-note\">Deactivated ${formatDate(p.deactivatedAt)}</div>")
                    }
                }
                val deactivateAction = if (isInactive) {
                    "<button class=\"btn btn-secondary btn-small\" data-action=\"reactivatePet\" data-arg=\"${p.id}\">Reactivate</button>"
                } else {
                    "<button class=\"btn btn-danger btn-small\" data-action=\"deactivatePet\" data-arg=\"${p.id}\">Deactivate</button>"
                }
                "<tr><td>$name</td><td>${p.type}</td><td>$statusCell</td><td>Rescuer #${p.rescuerId}</td>" +
                    "<td><a href=\"/pet/${p.id}\" class=\"btn btn-secondary btn-small\">View</a> $deactivateAction</td></tr>"
            } + "</tbody></table></div>"
    }

    private fun deactivatePet(id: Int) {
        if (!window.confirm(I18n.t("confirmDeactivatePet"))) return
        window.asDynamic().fetch("/api/admin/pets/$id/deactivate", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to deactivate pet')")
            loadPetsAdmin()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to deactivate pet") }
    }

    private fun reactivatePet(id: Int) {
        window.asDynamic().fetch("/api/admin/pets/$id/reactivate", js("({method: 'POST', credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) throw js("new Error('Failed to reactivate pet')")
            loadPetsAdmin()
        }.catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Failed to reactivate pet") }
    }
}
