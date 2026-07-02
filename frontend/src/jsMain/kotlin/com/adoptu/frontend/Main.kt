package com.adoptu.frontend

import com.adoptu.frontend.pages.AdminPageModule
import com.adoptu.frontend.pages.AdminSheltersPageModule
import com.adoptu.frontend.pages.AdminSterilizationLocationsPageModule
import com.adoptu.frontend.pages.EmailChangeVerificationPageModule
import com.adoptu.frontend.pages.ForgotPasswordPageModule
import com.adoptu.frontend.pages.IndexPageModule
import com.adoptu.frontend.pages.LoginPageModule
import com.adoptu.frontend.pages.MagicLinkLoginPageModule
import com.adoptu.frontend.pages.MyPetsPageModule
import com.adoptu.frontend.pages.PetDetailPageModule
import com.adoptu.frontend.pages.PetFoodPageModule
import com.adoptu.frontend.pages.PhotographersPageModule
import com.adoptu.frontend.pages.ProfilePageModule
import com.adoptu.frontend.pages.RegisterPageModule
import com.adoptu.frontend.pages.ResetPasswordPageModule
import com.adoptu.frontend.pages.SheltersPageModule
import com.adoptu.frontend.pages.SterilizationLocationsPageModule
import com.adoptu.frontend.pages.TemporalHomeBlockPageModule
import com.adoptu.frontend.pages.TemporalHomeProfilePageModule
import com.adoptu.frontend.pages.TemporalHomeSearchPageModule
import kotlinx.browser.window

fun main() {
    console.log("Frontend main() started, path: " + window.location.pathname)
    try {
        val path = window.location.pathname

        window.asDynamic().AdoptuI18n = I18n
        window.asDynamic().AdoptuCommon = CommonModule
        window.asDynamic().AdoptuWebAuthn = WebAuthnModule
        window.asDynamic().AdoptuIndexPage = IndexPageModule
        window.asDynamic().AdoptuLoginPage = LoginPageModule
        window.asDynamic().AdoptuProfilePage = ProfilePageModule
        window.asDynamic().AdoptuRegisterPage = RegisterPageModule
        window.asDynamic().AdoptuMyPets = MyPetsPageModule

        CommonModule.initI18n(null).then<Unit> {
            try {
                when {
                    path == "/" || path == "/pets" -> IndexPageModule.init()
                    path == "/login" || path == "/login/" -> LoginPageModule.init()
                    path == "/register" || path == "/register/" -> RegisterPageModule.init()
                    path == "/profile" || path == "/profile/" -> ProfilePageModule.init()
                    path == "/my-pets" || path == "/my-pets/" -> MyPetsPageModule.init()
                    path.startsWith("/pet/") -> PetDetailPageModule.init()
                    path == "/pet-food" || path == "/pet-food/" -> PetFoodPageModule.init()
                    path == "/shelters" || path == "/shelters/" -> SheltersPageModule.init()
                    path == "/admin/shelters" || path == "/admin/shelters/" -> AdminSheltersPageModule.init()
                    path == "/temporal-homes" || path == "/temporal-homes/" -> TemporalHomeSearchPageModule.init()
                    path == "/temporal-home" || path == "/temporal-home/" -> TemporalHomeProfilePageModule.init()
                    path.startsWith("/temporal-home/block/") -> TemporalHomeBlockPageModule.init()
                    path == "/photographers" || path == "/photographers/" -> PhotographersPageModule.init()
                    path == "/sterilization-locations" || path == "/sterilization-locations/" -> SterilizationLocationsPageModule.init()
                    path == "/admin/sterilization-locations" || path == "/admin/sterilization-locations/" -> AdminSterilizationLocationsPageModule.init()
                    path == "/admin" || path == "/admin/" -> AdminPageModule.init()
                    path == "/forgot-password" || path == "/forgot-password/" -> ForgotPasswordPageModule.init()
                    path == "/reset-password" || path == "/reset-password/" -> ResetPasswordPageModule.init()
                    path == "/magic-link-login" || path == "/magic-link-login/" -> MagicLinkLoginPageModule.init()
                    path == "/verify-email-change" || path == "/verify-email-change/" -> EmailChangeVerificationPageModule.init()
                    else -> {}
                }
            } catch (e: dynamic) {}
        }
    } catch (e: dynamic) {}
}
