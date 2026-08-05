package com.adoptu.mocks

import com.adoptu.adapters.db.*
import com.universaliun.ratelimit.backend.adapter.out.persistence.tables.RateLimitStateTable
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
                    PetFavorites,
                    Pets,
                    WebAuthnCredentials,
                    UserPasswords,
                    UserActiveRoles,
                    PendingRoleActivations,
                    TemporalHomeRequests,
                    BlockedRescuers,
                    UrgentReportPages,
                    UrgentReports,
                    UrgentRescuerProfiles,
                    LostFoundReports,
                    SavedSearches,
                    Photographers,
                    TemporalHomes,
                    SterilizationLocations,
                    AnimalShelters,
                    UserShelters,
                    UserSterilizationLocations,
                    Users,
                    CryptoKeys,
                    WebAuthnChallenges,
                    RateLimitStateTable,
                    AuthKitJwtKeys,
                    AuthKitPasskeyCeremonies,
                    AuthKitRefreshTokens
                )
            } catch (e: Exception) {
                // Tables may not exist on first run, ignore
            }
            SchemaUtils.create(
                Users,
                CryptoKeys,
                WebAuthnChallenges,
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
                PendingRoleActivations,
                AnimalShelters,
                SterilizationLocations,
                UserShelters,
                UserSterilizationLocations,
                Photographers,
                TemporalHomes,
                BlockedRescuers,
                TemporalHomeRequests,
                UrgentRescuerProfiles,
                UrgentReports,
                UrgentReportPages,
                LostFoundReports,
                SavedSearches,
                Pets,
                PetImages,
                AdoptionRequests,
                PhotographyRequests,
                PetFavorites,
                RateLimitStateTable,
                AuthKitJwtKeys,
                AuthKitPasskeyCeremonies,
                AuthKitRefreshTokens
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
            exec("DELETE FROM urgent_report_pages")
            exec("DELETE FROM urgent_reports")
            exec("DELETE FROM urgent_rescuer_profiles")
            exec("DELETE FROM lost_found_reports")
            exec("DELETE FROM saved_searches")
            exec("DELETE FROM temporal_homes")
            exec("DELETE FROM adoption_requests")
            exec("DELETE FROM pet_images")
            exec("DELETE FROM pet_favorites")
            exec("DELETE FROM pets")
            exec("DELETE FROM webauthn_credentials")
            exec("DELETE FROM photography_requests")
            exec("DELETE FROM user_active_roles")
            exec("DELETE FROM pending_role_activations")
            exec("DELETE FROM photographers")
            exec("DELETE FROM users")
            exec("DELETE FROM animal_shelters")
            exec("DELETE FROM sterilization_locations")
            exec("DELETE FROM user_shelters")
            exec("DELETE FROM user_sterilization_locations")
            exec("DELETE FROM crypto_keys")
            exec("DELETE FROM webauthn_challenges")
            exec("DELETE FROM rate_limit_state")
            exec("DELETE FROM authkit_passkey_ceremonies")
            exec("DELETE FROM authkit_refresh_tokens")
            exec("DELETE FROM authkit_jwt_keys")
        }
    }
}
