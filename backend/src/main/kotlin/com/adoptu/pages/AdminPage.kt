package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.adminPage(navParams: NavParams = NavParams()) {
    commonHead("Admin - Adopt-U")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "adminPanel"; +"Admin Panel" }
            
            div(classes = "admin-tabs") {
                button(classes = "admin-tab-btn active") {
                    id = "tab-users"
                    attributes["data-i18n"] = "manageUsers"
                    +"Manage Users"
                }
                button(classes = "admin-tab-btn") {
                    id = "tab-pets"
                    attributes["data-i18n"] = "managePets"
                    +"Manage Pets"
                }
            }
            
            div(classes = "admin-tab-content") {
                id = "users-tab"
                div(classes = "admin-filter-bar") {
                    select { id = "user-role-filter"
                        option { value = ""; +"All roles" }
                        option { value = "ADMIN"; +"ADMIN" }
                        option { value = "RESCUER"; +"RESCUER" }
                        option { value = "ADOPTER"; +"ADOPTER" }
                        option { value = "PHOTOGRAPHER"; +"PHOTOGRAPHER" }
                        option { value = "TEMPORAL_HOME"; +"TEMPORAL_HOME" }
                        option { value = "SHELTER"; +"SHELTER" }
                        option { value = "STERILIZATION_SERVICE"; +"STERILIZATION_SERVICE" }
                    }
                    input(InputType.search) { id = "user-search"; placeholder = "Search by email or name" }
                    label { input(InputType.checkBox) { id = "user-show-inactive" }; +" Show inactive" }
                    label { input(InputType.checkBox) { id = "user-show-banned" }; +" Show banned users" }
                }
                div { id = "users-container"; +"" }
                div(classes = "admin-pagination") { id = "users-pagination" }
            }

            // Search/moderation overview, paginated via GET /api/admin/pets. Separate from
            // the /my-pets link below (kept for full add/edit) - see adminPetsRoutes() in
            // PetsRoutes.kt for why this doesn't reuse getMine()/getAllUnfiltered().
            div(classes = "admin-tab-content hidden") {
                id = "pets-tab"
                p { attributes["data-i18n"] = "managePetsDescription"; +"Manage all pet pages. Add or remove pets." }
                a("/my-pets") { classes = setOf("btn"); attributes["data-i18n"] = "managePetsBtn"; +"Manage Pets" }
                div(classes = "admin-filter-bar") {
                    input(InputType.search) { id = "pet-search"; placeholder = "Search by pet name" }
                    label { input(InputType.checkBox) { id = "pet-show-inactive" }; +" Show inactive" }
                }
                div { id = "pets-admin-container"; +"" }
                div(classes = "admin-pagination") { id = "pets-pagination" }
            }

            div(classes = "form-modal hidden") {
                id = "ban-modal"
                div(classes = "form-modal-content card-bg") {
                    h2 { attributes["data-i18n"] = "banUser"; +"Ban User" }
                    p { id = "ban-user-name"; +"" }
                    div(classes = "form-row") {
                        label { attributes["data-i18n"] = "banReason"; +"Reason (optional)" }
                        textArea { id = "ban-reason"; rows = "4" }
                    }
                    div(classes = "form-actions") {
                        button(type = ButtonType.button) {
                            classes = setOf("btn", "btn-danger")
                            attributes["data-i18n"] = "banUser"
                            attributes["data-action"] = "confirmBan"
                            +"Ban User"
                        }
                        button(type = ButtonType.button) {
                            classes = setOf("btn", "btn-secondary")
                            attributes["data-i18n"] = "cancel"
                            attributes["data-action"] = "hideBanModal"
                            +"Cancel"
                        }
                    }
                }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
