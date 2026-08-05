package com.adoptu.adapters.db

import com.adoptu.common.Country
import org.jetbrains.exposed.v1.core.Table
import java.math.BigDecimal

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 255).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val language = varchar("language", 10).default("en")
    val country = enumerationByName("country", 100, Country::class).nullable()
    val createdAt = long("created_at")
    val lastAcceptedPrivacyPolicy = long("last_accepted_privacy_policy").nullable()
    val lastAcceptedTermsAndConditions = long("last_accepted_terms_and_conditions").nullable()
    val isEmailVerified = bool("is_email_verified").default(false)
    val isBanned = bool("is_banned").default(false)
    val banReason = varchar("ban_reason", 500).nullable()
    // Auditable soft-deactivation, independent of isBanned - null deactivatedAt means
    // active. Deliberately not a plain isActive boolean: reactivating without recording
    // who/when deactivated a user would throw away the moderation trail. Mirrors the
    // Auditable/deletedAt+deletedBy pattern, adapted to this app's actual verb.
    val deactivatedAt = long("deactivated_at").nullable()
    val deactivatedBy = integer("deactivated_by").references(id).nullable()
    // AuthKit's UserRepositoryPort.findByResetTokenHash/updateResetToken expect ONE shared
    // token slot per user, reused across magic-link/setup/confirm-email/password-reset -- this
    // app instead has separate MagicLinkTokens/PasswordResetTokens/EmailVerificationTokens
    // tables per purpose. Rather than repurpose one of those (risking a magic-link token also
    // working as a password-reset token), these are new, dedicated columns used ONLY by the
    // AuthKit bridge (AdoptuUserRepositoryAdapter) going forward -- the old per-purpose tables
    // stay untouched until their native services are fully retired.
    val resetTokenHash = varchar("reset_token_hash", 255).nullable()
    val resetTokenExpiresAt = long("reset_token_expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object EmailVerificationTokens : Table("email_verification_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object UserPasswords : Table("user_passwords") {
    val userId = integer("user_id").references(Users.id)
    val passwordHash = text("password_hash")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object MagicLinkTokens : Table("magic_link_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")
    val usedAt = long("used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object LoginAttempts : Table("login_attempts") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255)
    val successful = bool("successful")
    val attemptedAt = long("attempted_at")

    override val primaryKey = PrimaryKey(id)
}

object PasswordResetTokens : Table("password_reset_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object EmailChangeTokens : Table("email_change_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val newEmail = varchar("new_email", 255)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// Verifies the standalone "contact email" field on a self-service provider profile
// (shelter, sterilization location) when it differs from the account's own login
// email — that field is shown publicly, so it must be proven ownable before the
// profile can be activated, same as the account email itself.
object ProfileEmailVerificationTokens : Table("profile_email_verification_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val profileType = varchar("profile_type", 20)
    val email = varchar("email", 255)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// Backs the one-click "report as spam / block this rescuer" link emailed to a
// temporal home when a rescuer sends them a request (see
// TemporalHomeService.sendRequest). The link must work without the recipient
// being logged in, so - like every other no-login action link in this
// codebase - it's gated by a random single-use token rather than trusting the
// temporalHomeId/rescuerId embedded in the URL directly (those are guessable
// sequential integers with no secret component).
object SpamReportTokens : Table("spam_report_tokens") {
    val id = integer("id").autoIncrement()
    val temporalHomeId = integer("temporal_home_id").references(Users.id)
    val rescuerId = integer("rescuer_id").references(Users.id)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")
    val usedAt = long("used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object EmailVerificationAttempts : Table("email_verification_attempts") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Photographers : Table("photographers") {
    val userId = integer("user_id").references(Users.id)
    val photographerFee = decimal("photographer_fee", 10, 2).nullable()
    val photographerCurrency = varchar("photographer_currency", 10).nullable()
    val country = enumerationByName("country", 100, Country::class).nullable()
    val state = varchar("state", 100).nullable()

    override val primaryKey = PrimaryKey(userId)
}

object UserActiveRoles : Table("user_active_roles") {
    val userId = integer("user_id").references(Users.id)
    val role = varchar("role", 50)

    override val primaryKey = PrimaryKey(userId, role)
}

// Roles selected at registration for role types that require a verified email before
// activation (RESCUER, PHOTOGRAPHER, TEMPORAL_HOME, SHELTER, STERILIZATION_SERVICE - see
// ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION in WebAuthnService.kt) are recorded here
// instead of being silently dropped, then consumed and actually granted the moment email
// verification completes (UserService.verifyToken()).
object PendingRoleActivations : Table("pending_role_activations") {
    val userId = integer("user_id").references(Users.id)
    val role = varchar("role", 50)

    override val primaryKey = PrimaryKey(userId, role)
}

object WebAuthnCredentials : Table("webauthn_credentials") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val credentialId = varchar("credential_id", 500)
    val attestedCredentialDataBase64 = text("attested_credential_data")
    val signCount = long("sign_count")
    val transports = varchar("transports", 255).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// Persists the RSA keypair CryptoService uses to decrypt passwords encrypted client-side (see
// CryptoService.kt) so every ECS task decrypts with the SAME key. Previously each JVM generated
// and cached its own keypair at startup with no persistence, so a public key fetched from one
// task's /api/auth/encryption-key could not be decrypted by a different task handling the
// follow-up login/register request - a single fixed-id row acts as the shared singleton.
object CryptoKeys : Table("crypto_keys") {
    val id = integer("id")
    val publicKey = text("public_key")
    val privateKey = text("private_key")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// Persists WebAuthn registration/assertion challenges (see WebAuthnService.ChallengeStore) so a
// challenge created on one ECS task is still found when the browser posts the signed response
// back to a different task. Previously an in-memory map keyed the same way - also unbounded,
// since nothing ever expired it, only removed it on retrieval - expiresAt lets stale rows be
// purged instead of accumulating forever.
object WebAuthnChallenges : Table("webauthn_challenges") {
    val id = integer("id").autoIncrement()
    val challengeKey = varchar("challenge_key", 320).uniqueIndex()
    val challenge = varchar("challenge", 100)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(id)
}

// Persists AuthKit's WebAuthn ceremony state (com.universaliun.auth.backend.domain.port.out.
// PasskeyCeremonyStorePort) between the "start" and "finish" half of a registration/login,
// keyed by AuthKit's own requestId -- same "shared Postgres store instead of process-local state"
// reasoning as CryptoKeys/WebAuthnChallenges above (bug-210): a challenge started on one ECS task
// must still be found by whichever task handles the matching finish request. payloadJson is
// Yubico's own PublicKeyCredentialCreationOptions.toJson()/AssertionRequest.toJson() output --
// AuthKit generates and consumes these itself via the matching fromJson(), so this table never
// needs to understand their internal shape.
// Persists the RSA keypair AuthKit signs/verifies JWTs with, so every ECS task issues and accepts
// tokens signed by the SAME key -- same singleton-row-per-deployment reasoning as CryptoKeys above
// (bug-210). This app never issued JWTs before AuthKit (cookie sessions were the whole story), so
// there was no existing keypair to bridge onto.
object AuthKitJwtKeys : Table("authkit_jwt_keys") {
    val id = integer("id")
    val publicKey = text("public_key")
    val privateKey = text("private_key")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object AuthKitPasskeyCeremonies : Table("authkit_passkey_ceremonies") {
    val id = integer("id").autoIncrement()
    val requestId = varchar("request_id", 255).uniqueIndex()
    val type = varchar("type", 20) // "REGISTRATION", "LOGIN", or "SIGNUP"
    val userId = integer("user_id").nullable() // only set for REGISTRATION
    // Only set for SIGNUP -- no user exists yet at that point, so email/displayName travel with
    // the challenge instead (see AuthKit's SignupPasskeyChallenge).
    val signupEmail = varchar("signup_email", 255).nullable()
    val signupDisplayName = varchar("signup_display_name", 255).nullable()
    val payloadJson = text("payload_json")
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(id)
}

// Persists AuthKit's refresh tokens (com.universaliun.auth.backend.domain.port.out.
// RefreshTokenRepositoryPort) durably -- this app previously had no refresh-token concept at all
// (cookie sessions were the whole story), so AuthKit's own in-memory default would silently log
// every user out on every deploy/restart/task-recycle. tokenHash is unique+indexed (the hot
// lookup path on every /refresh call); userId backs revokeAllForUser (logout-everywhere / reuse
// detection).
object AuthKitRefreshTokens : Table("authkit_refresh_tokens") {
    val id = varchar("id", 36) // UUID string, matches AuthKit's RefreshToken.id type
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val userId = integer("user_id").references(Users.id)
    val expiresAt = long("expires_at")
    val revoked = bool("revoked").default(false)
    val deviceInfo = varchar("device_info", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object Pets : Table("pets") {
    val id = integer("id").autoIncrement()
    val rescuerId = integer("rescuer_id").references(Users.id)
    val name = varchar("name", 255)
    val type = varchar("type", 50) // DOG, CAT, BIRD, FISH
    val breed = varchar("breed", 255).nullable()
    val description = text("description")
    val weight = decimal("weight", 6, 2)
    val ageYears = integer("age_years")
    val ageMonths = integer("age_months")
    val sex = varchar("sex", 10).default("MALE") // MALE, FEMALE
    val status = varchar("status", 50) // AVAILABLE, ADOPTED, PENDING
    val color = varchar("color", 100).nullable()
    val size = varchar("size", 20).nullable() // SMALL, MEDIUM, LARGE
    val temperament = varchar("temperament", 100).nullable()
    val isSterilized = bool("is_sterilized").default(false)
    val isMicrochipped = bool("is_microchipped").default(false)
    val microchipId = varchar("microchip_id", 100).nullable()
    val vaccinations = text("vaccinations").nullable()
    val isGoodWithKids = bool("is_good_with_kids").default(true)
    val isGoodWithDogs = bool("is_good_with_dogs").default(true)
    val isGoodWithCats = bool("is_good_with_cats").default(true)
    val isHouseTrained = bool("is_house_trained").default(false)
    val energyLevel = varchar("energy_level", 20).nullable() // LOW, MEDIUM, HIGH
    val rescueDate = long("rescue_date").nullable()
    val rescueLocation = varchar("rescue_location", 255).nullable()
    // Nullable at the DB level: ~190 pre-existing pet rows have no reliable way to derive a
    // country from the free-text rescueLocation field, so we leave them null rather than guess
    // (see feature brief for "mandatory country-based search"). No backfill is performed.
    val country = enumerationByName("country", 100, Country::class).nullable()
    val specialNeeds = text("special_needs").nullable()
    val adoptionFee = decimal("adoption_fee", 10, 2).default(BigDecimal.ZERO)
    val currency = varchar("currency", 10).default("USD")
    val isUrgent = bool("is_urgent").default(false)
    // isPromoted is a free, needs-based "this pet urgently needs a new home" flag set by the
    // rescuer themselves - NOT a paid boost. Every promoted pet gets equal, non-monetized
    // priority (sorted first in getAll()); requires a reason category (see PromotedReason).
    val isPromoted = bool("is_promoted").default(false)
    val promotedReason = varchar("promoted_reason", 50).nullable()
    val promotedReasonDetail = text("promoted_reason_detail").nullable()
    // Single video per pet (not a gallery like pet_images) - Adopt-a-Pet's own data shows video
    // listings get far more interest than photo-only ones. No transcoding pipeline; stored as
    // whatever mp4/webm the browser uploaded (see PetService.uploadAndSetVideo).
    val videoUrl = text("video_url").nullable()
    val createdAt = long("created_at")
    // Auditable soft-deactivation - a non-destructive alternative to the existing hard
    // delete() (which cascades to pet_images/adoption_requests). Independent of `status`
    // (AVAILABLE/PENDING/ADOPTED is adoption progress, not moderation state). See the
    // matching fields on Users for the same pattern/rationale.
    val deactivatedAt = long("deactivated_at").nullable()
    val deactivatedBy = integer("deactivated_by").references(Users.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

object PetImages : Table("pet_images") {
    val id = integer("id").autoIncrement()
    val petId = integer("pet_id").references(Pets.id)
    val imageUrl = text("image_url") // S3 URL
    val isPrimary = bool("is_primary").default(false)
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)
}

object AdoptionRequests : Table("adoption_requests") {
    val id = integer("id").autoIncrement()
    val petId = integer("pet_id").references(Pets.id)
    val adopterId = integer("adopter_id").references(Users.id)
    val message = text("message")
    val status = varchar("status", 50) // PENDING, APPROVED, REJECTED
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PhotographyRequests : Table("photography_requests") {
    val id = integer("id").autoIncrement()
    val photographerId = integer("photographer_id").references(Users.id)
    val requesterId = integer("requester_id").references(Users.id)
    val petId = integer("pet_id").references(Pets.id).nullable()
    val message = text("message").nullable()
    val status = varchar("status", 50) // PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
    val scheduledDate = long("scheduled_date").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TemporalHomes : Table("temporal_homes") {
    val userId = integer("user_id").references(Users.id)
    val alias = varchar("alias", 255)
    val country = enumerationByName("country", 100, Country::class)
    val state = varchar("state", 100).nullable()
    val city = varchar("city", 100)
    val zip = varchar("zip", 20).nullable()
    val neighborhood = varchar("neighborhood", 100).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(userId)
}

object BlockedRescuers : Table("blocked_rescuers") {
    val id = integer("id").autoIncrement()
    val temporalHomeId = integer("temporal_home_id").references(Users.id)
    val rescuerId = integer("rescuer_id").references(Users.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TemporalHomeRequests : Table("temporal_home_requests") {
    val id = integer("id").autoIncrement()
    val temporalHomeId = integer("temporal_home_id").references(Users.id)
    val rescuerId = integer("rescuer_id").references(Users.id)
    val petId = integer("pet_id").references(Pets.id).nullable()
    val message = text("message")
    val status = varchar("status", 50) // SENT, READ
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// LocationInputMode/UrgentReportStatus/UrgentReportPageStatus/UrgentDangerType (dto/input/UrgentRescueDto.kt)
// are stored as their enum .name via enumerationByName, same convention as UrgentReports.status below.
object UrgentRescuerProfiles : Table("urgent_rescuer_profiles") {
    val userId = integer("user_id").references(Users.id)
    val phone = varchar("phone", 50)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val radiusKm = double("radius_km")
    val inputMode = varchar("input_mode", 20)
    val zoneCountry = enumerationByName("zone_country", 100, Country::class).nullable()
    val zoneState = varchar("zone_state", 100).nullable()
    val zoneCity = varchar("zone_city", 100).nullable()
    val active = bool("active").default(true)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(userId)
}

object UrgentReports : Table("urgent_reports") {
    val id = integer("id").autoIncrement()
    val reporterUserId = integer("reporter_user_id").references(Users.id).nullable()
    val reporterEmail = varchar("reporter_email", 255)
    val reporterPhone = varchar("reporter_phone", 50).nullable()
    val description = text("description")
    val dangerType = varchar("danger_type", 20)
    val photoUrl = varchar("photo_url", 500).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val locationLabel = varchar("location_label", 255)
    val status = varchar("status", 20)
    val acceptedByUserId = integer("accepted_by_user_id").references(Users.id).nullable()
    val acceptedAt = long("accepted_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// One row per rescuer paged for a report - "first accept wins" and each rescuer's pending-pages
// dashboard both query this, not UrgentReports directly (a report can page many rescuers).
object UrgentReportPages : Table("urgent_report_pages") {
    val id = integer("id").autoIncrement()
    val reportId = integer("report_id").references(UrgentReports.id)
    val rescuerId = integer("rescuer_id").references(Users.id)
    val token = varchar("token", 255).uniqueIndex()
    val status = varchar("status", 20)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// LOST (owner missing a pet) and FOUND (someone found a stray) reports share one table,
// distinguished by kind - matching cross-references the opposite kind (see LostFoundService).
object LostFoundReports : Table("lost_found_reports") {
    val id = integer("id").autoIncrement()
    val kind = varchar("kind", 10)
    val reporterUserId = integer("reporter_user_id").references(Users.id).nullable()
    val reporterEmail = varchar("reporter_email", 255)
    val reporterPhone = varchar("reporter_phone", 50).nullable()
    val petType = varchar("pet_type", 50).nullable()
    val description = text("description")
    val photoUrl = varchar("photo_url", 500).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val locationLabel = varchar("location_label", 255)
    val country = enumerationByName("country", 100, Country::class)
    val lastSeenAt = long("last_seen_at")
    val status = varchar("status", 20)
    val resolveToken = varchar("resolve_token", 255).uniqueIndex()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// null type = "any type" for this country. No state/city - the pets browse page only ever
// filters by type+country server-side (sex is client-side only) - see PetService/PetsRoutes.
object SavedSearches : Table("saved_searches") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val type = varchar("type", 50).nullable()
    val country = enumerationByName("country", 100, Country::class)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PetFavorites : Table("pet_favorites") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val petId = integer("pet_id").references(Pets.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(userId, petId) }
}

object AnimalShelters : Table("animal_shelters") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id).nullable()
    val name = varchar("name", 255)
    val country = enumerationByName("country", 100, Country::class)
    val state = varchar("state", 100).nullable()
    val city = varchar("city", 100)
    val neighborhood = varchar("neighborhood", 100).nullable()
    val address = varchar("address", 500)
    val zip = varchar("zip", 20).nullable()
    val phone = varchar("phone", 50).nullable()
    val email = varchar("email", 255).nullable()
    val website = varchar("website", 500).nullable()
    val fiscalId = varchar("fiscal_id", 100).nullable()
    val bankName = varchar("bank_name", 255).nullable()
    val accountHolderName = varchar("account_holder_name", 255).nullable()
    val accountNumber = varchar("account_number", 100).nullable()
    val iban = varchar("iban", 50).nullable()
    val swiftBic = varchar("swift_bic", 20).nullable()
    val currency = varchar("currency", 10).default("USD")
    val description = text("description").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object SterilizationLocations : Table("sterilization_locations") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id).nullable()
    val name = varchar("name", 255)
    val country = enumerationByName("country", 100, Country::class)
    val state = varchar("state", 100).nullable()
    val city = varchar("city", 100)
    val neighborhood = varchar("neighborhood", 100).nullable()
    val address = varchar("address", 500)
    val zip = varchar("zip", 20).nullable()
    val phone = varchar("phone", 50).nullable()
    val email = varchar("email", 255).nullable()
    val website = varchar("website", 500).nullable()
    val description = text("description").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object UserShelters : Table("user_shelters") {
    val userId = integer("user_id").references(Users.id)
    val name = varchar("name", 255)
    val country = enumerationByName("country", 100, Country::class)
    val state = varchar("state", 100).nullable()
    val city = varchar("city", 100)
    val neighborhood = varchar("neighborhood", 100).nullable()
    val address = varchar("address", 500)
    val zip = varchar("zip", 20).nullable()
    val phone = varchar("phone", 50).nullable()
    val email = varchar("email", 255).nullable()
    val emailVerified = bool("email_verified").default(false)
    val website = varchar("website", 500).nullable()
    val fiscalId = varchar("fiscal_id", 100).nullable()
    val bankName = varchar("bank_name", 255).nullable()
    val accountHolderName = varchar("account_holder_name", 255).nullable()
    val accountNumber = varchar("account_number", 100).nullable()
    val iban = varchar("iban", 50).nullable()
    val swiftBic = varchar("swift_bic", 20).nullable()
    val currency = varchar("currency", 10).default("USD")
    val description = text("description").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object UserSterilizationLocations : Table("user_sterilization_locations") {
    val userId = integer("user_id").references(Users.id)
    val name = varchar("name", 255)
    val country = enumerationByName("country", 100, Country::class)
    val state = varchar("state", 100).nullable()
    val city = varchar("city", 100)
    val neighborhood = varchar("neighborhood", 100).nullable()
    val address = varchar("address", 500)
    val zip = varchar("zip", 20).nullable()
    val phone = varchar("phone", 50).nullable()
    val email = varchar("email", 255).nullable()
    val emailVerified = bool("email_verified").default(false)
    val website = varchar("website", 500).nullable()
    val description = text("description").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
