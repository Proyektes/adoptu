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
                div { id = "users-container"; +"" }
            }
            
            div(classes = "admin-tab-content") {
                id = "pets-tab"
                style = "display: none;"
                p { attributes["data-i18n"] = "managePetsDescription"; +"Manage all pet pages. Add or remove pets." }
                div { id = "message"; +"" }
                a("/my-pets") { classes = setOf("btn"); attributes["data-i18n"] = "managePetsBtn"; +"Manage Pets" }
                div { id = "pets"; classes = setOf("pet-grid"); style = "margin-top: 2rem;"; +"" }
            }
            
            div(classes = "form-modal") {
                id = "ban-modal"
                style = "display: none;"
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
                            onClick = "confirmBan()"
                            +"Ban User"
                        }
                        button(type = ButtonType.button) {
                            classes = setOf("btn", "btn-secondary")
                            attributes["data-i18n"] = "cancel"
                            onClick = "hideBanModal()"
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
