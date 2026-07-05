package com.adoptu.mocks

import com.adoptu.adapters.db.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object TestDatabase {
    fun initH2() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            try {
                SchemaUtils.drop(
                    LoginAttempts,
                    EmailVerificationAttempts,
                    EmailVerificationTokens,
                    EmailChangeTokens,
                    ProfileEmailVerificationTokens,
                    PasswordResetTokens,
                    MagicLinkTokens,
                    SpamReportTokens,
                    PetImages,
                    AdoptionRequests,
                    PhotographyRequests,
                    Pets,
                    WebAuthnCredentials,
                    UserPasswords,
                    UserActiveRoles,
                    TemporalHomeRequests,
                    BlockedRescuers,
                    Photographers,
                    TemporalHomes,
                    SterilizationLocations,
                    AnimalShelters,
                    UserShelters,
                    UserSterilizationLocations,
                    Users
                )
            } catch (e: Exception) {
                // Tables may not exist on first run, ignore
            }
            SchemaUtils.create(
                Users,
                LoginAttempts,
                EmailVerificationAttempts,
                EmailVerificationTokens,
                EmailChangeTokens,
                ProfileEmailVerificationTokens,
                PasswordResetTokens,
                MagicLinkTokens,
                SpamReportTokens,
                UserPasswords,
                WebAuthnCredentials,
                UserActiveRoles,
                AnimalShelters,
                SterilizationLocations,
                UserShelters,
                UserSterilizationLocations,
                Photographers,
                TemporalHomes,
                BlockedRescuers,
                TemporalHomeRequests,
                Pets,
                PetImages,
                AdoptionRequests,
                PhotographyRequests
            )
        }
    }

    fun clearAllData() {
        transaction {
            exec("DELETE FROM login_attempts")
            exec("DELETE FROM email_verification_attempts")
            exec("DELETE FROM email_verification_tokens")
            exec("DELETE FROM email_change_tokens")
            exec("DELETE FROM profile_email_verification_tokens")
            exec("DELETE FROM password_reset_tokens")
            exec("DELETE FROM magic_link_tokens")
            exec("DELETE FROM spam_report_tokens")
            exec("DELETE FROM user_passwords")
            exec("DELETE FROM temporal_home_requests")
            exec("DELETE FROM blocked_rescuers")
            exec("DELETE FROM temporal_homes")
            exec("DELETE FROM adoption_requests")
            exec("DELETE FROM pet_images")
            exec("DELETE FROM pets")
            exec("DELETE FROM webauthn_credentials")
            exec("DELETE FROM photography_requests")
            exec("DELETE FROM user_active_roles")
            exec("DELETE FROM photographers")
            exec("DELETE FROM users")
            exec("DELETE FROM animal_shelters")
            exec("DELETE FROM sterilization_locations")
            exec("DELETE FROM user_shelters")
            exec("DELETE FROM user_sterilization_locations")
        }
    }
}
