package com.adoptu.di

import com.adoptu.adapters.authkit.AdoptuPasskeyCeremonyStoreAdapter
import com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuRefreshTokenRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter
import com.adoptu.adapters.captcha.TurnstileCaptchaAdapter
import com.adoptu.adapters.db.repositories.*
import com.adoptu.adapters.geocoding.NominatimGeocodingAdapter
import com.adoptu.adapters.notification.NotificationEmailAdapter
import com.adoptu.adapters.notification.SnsSmsAdapter
import com.adoptu.adapters.storage.S3ImageStorageAdapter
import com.universaliun.auth.backend.domain.port.out.PasskeyCeremonyStorePort
import com.universaliun.auth.backend.domain.port.out.PasskeyCredentialRepositoryPort
import com.universaliun.auth.backend.domain.port.out.RefreshTokenRepositoryPort
import com.universaliun.auth.backend.domain.port.out.UserRepositoryPort as KitUserRepositoryPort
import com.universaliun.email.common.EmailSenderPort
import com.universaliun.ratelimit.backend.adapter.out.persistence.ExposedRateLimitStateAdapter
import com.universaliun.ratelimit.common.RateLimiter
import com.adoptu.config.AppConfig
import com.adoptu.ports.*
import com.adoptu.services.*
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.validation.*
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun appModule(config: AppConfig) = module {
    single { config }
    single<Clock> { Clock.System }
    single { WebAuthnService(get(), get()) }
    // AuthKit bridge adapters -- registered as their own concrete type (so AuthRoutes.kt can
    // inject them directly, e.g. for the has-passkey check and post-registration role
    // assignment) AND bound to the AuthKit port they implement (so authKoinModule(...) in
    // Application.kt can resolve the same singleton instances via get()).
    single { AdoptuUserRepositoryAdapter() }
    single<KitUserRepositoryPort> { get<AdoptuUserRepositoryAdapter>() }
    single { AdoptuPasskeyCredentialRepositoryAdapter() }
    single<PasskeyCredentialRepositoryPort> { get<AdoptuPasskeyCredentialRepositoryAdapter>() }
    single { AdoptuRefreshTokenRepositoryAdapter() }
    single<RefreshTokenRepositoryPort> { get<AdoptuRefreshTokenRepositoryAdapter>() }
    single { AdoptuPasskeyCeremonyStoreAdapter() }
    single<PasskeyCeremonyStorePort> { get<AdoptuPasskeyCeremonyStoreAdapter>() }
    single<PetRepositoryPort> { PetRepositoryImpl(get()) }
    single<UserRepositoryPort> { UserRepository(get()) }
    single { UserRepository(get()) }
    single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
    single<TemporalHomeRepositoryPort> { TemporalHomeRepositoryImpl(get(), get(), get()) }
    single<UserShelterRepositoryPort> { UserShelterRepository(get()) }
    single<UserSterilizationLocationRepositoryPort> { UserSterilizationLocationRepository(get()) }
    single<ShelterRepositoryPort> { ShelterRepository(get()) }
    single<SterilizationLocationRepositoryPort> { SterilizationLocationRepository(get()) }
    single<UrgentRescueRepositoryPort> { UrgentRescueRepositoryImpl(get()) }
    single<LostFoundRepositoryPort> { LostFoundRepositoryImpl(get()) }
    single<SavedSearchRepositoryPort> { SavedSearchRepositoryImpl(get()) }
    single<PetMedicalEventRepositoryPort> { PetMedicalEventRepositoryImpl(get()) }
    single<PetFosterPlacementRepositoryPort> { PetFosterPlacementRepositoryImpl(get(), get(), get()) }
    single<PetFavoriteRepositoryPort> { PetFavoriteRepositoryImpl(get()) }
    single<VolunteerRepositoryPort> { VolunteerRepositoryImpl(get(), get()) }
    single<GeocodingPort> { NominatimGeocodingAdapter() }
    single<ImageStoragePort> { createImageStorageAdapter(config) }
    single<EmailSenderPort> { emailSenderPortFromConfig(config) }
    single<NotificationPort> { NotificationEmailAdapter(get()) }
    single<SmsNotificationPort> {
        SnsSmsAdapter(
            region = config.propertyOrNull("sns.region")?.getString() ?: "us-east-1",
            accessKeyId = config.propertyOrNull("sns.access_key_id")?.getString(),
            secretAccessKey = config.propertyOrNull("sns.secret_access_key")?.getString(),
            endpoint = config.propertyOrNull("sns.endpoint")?.getString()
        )
    }
    single<CaptchaPort> { TurnstileCaptchaAdapter(config.propertyOrNull("turnstile.secretKey")?.getString() ?: "") }
    single { RateLimiter(ExposedRateLimitStateAdapter()) }
    single<PhotographerService> { PhotographerService(get(), get(), get(), get()) }
    single<UserService> { UserService(get(), get()) }
    single<PetService> { PetService(get(), get(), get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single<SavedSearchService> { SavedSearchService(get()) }
    single<PetMedicalEventService> { PetMedicalEventService(get(), get()) }
    single<PetFosterPlacementService> { PetFosterPlacementService(get(), get(), get(), get()) }
    single<MedicalReminderService> {
        MedicalReminderService(get(), get(), get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80")
    }
    single<PetFavoriteService> { PetFavoriteService(get(), get()) }
    single { RescuerDirectoryService(get(), get()) }
    single { VolunteerService(get(), get()) }
    single<TemporalHomeService> { TemporalHomeService(get(), get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single<UrgentRescueService> { UrgentRescueService(get(), get(), get(), get(), get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single<LostFoundService> { LostFoundService(get(), get(), get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { ProfileEmailVerificationService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { UserShelterService(get(), get()) }
    single { UserSterilizationLocationService(get(), get()) }
    single { ShelterService(get()) }
    single { SterilizationLocationService(get()) }
    single { EmailVerificationService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80", get()) }
    single { PasswordService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80", get()) }
    single { MagicLinkService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80", get(), get()) }
    single { EmailChangeService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { UsersValidationService() }
    single { PetsValidationService() }
    single { PhotographersValidationService() }
    single { SheltersValidationService() }
    single { SterilizationLocationsValidationService() }
    single { TemporalHomesValidationService() }
    single { AuthValidationService() }
}

internal fun createImageStorageAdapter(config: AppConfig): ImageStoragePort {
    val env = config.propertyOrNull("env")?.getString() ?: "prod"
    val prefix = "storage.$env"

    val bucketName = config.property("$prefix.bucket").getString()
    val region = config.propertyOrNull("$prefix.region")?.getString() ?: "us-east-1"
    val accessKeyId = config.propertyOrNull("$prefix.access_key_id")?.getString()
    val secretAccessKey = config.propertyOrNull("$prefix.secret_access_key")?.getString()
    val endpoint = config.propertyOrNull("$prefix.endpoint")?.getString()
    val pathStyleAccess = config.propertyOrNull("$prefix.path_style_access")?.getString()?.toBoolean() ?: false
    val publicUrl = config.propertyOrNull("$prefix.public_url")?.getString()

    return S3ImageStorageAdapter(
        bucketName = bucketName,
        region = region,
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        endpoint = endpoint,
        pathStyleAccess = pathStyleAccess,
        publicUrl = publicUrl
    )
}
