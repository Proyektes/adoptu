package com.adoptu.di

import com.adoptu.adapters.db.repositories.*
import com.adoptu.adapters.notification.SesEmailAdapter
import com.adoptu.adapters.storage.S3ImageStorageAdapter
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
    single { WebAuthnService(get(), get(), get(), get(), get(), config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com", config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost", config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption", getOrigins(config)) }
    single<PetRepositoryPort> { PetRepositoryImpl(get()) }
    single<UserRepositoryPort> { UserRepository(get()) }
    single { UserRepository(get()) }
    single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
    single<TemporalHomeRepositoryPort> { TemporalHomeRepositoryImpl(get(), get(), get()) }
    single<UserShelterRepositoryPort> { UserShelterRepository(get()) }
    single<UserSterilizationLocationRepositoryPort> { UserSterilizationLocationRepository(get()) }
    single<ShelterRepositoryPort> { ShelterRepository(get()) }
    single<SterilizationLocationRepositoryPort> { SterilizationLocationRepository(get()) }
    single<ImageStoragePort> { createImageStorageAdapter(config) }
    single<NotificationPort> { SesEmailAdapter(get()) }
    single<PhotographerService> { PhotographerService(get(), get(), get(), get()) }
    single<UserService> { UserService(get()) }
    single<PetService> { PetService(get(), get(), get(), get()) }
    single<TemporalHomeService> { TemporalHomeService(get(), get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { ProfileEmailVerificationService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { UserShelterService(get(), get()) }
    single { UserSterilizationLocationService(get(), get()) }
    single { ShelterService(get()) }
    single { SterilizationLocationService(get()) }
    single { EmailVerificationService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { PasswordService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { MagicLinkService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80", get()) }
    single { EmailChangeService(get(), get(), get(), config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:80") }
    single { UsersValidationService() }
    single { PetsValidationService() }
    single { PhotographersValidationService() }
    single { SheltersValidationService() }
    single { SterilizationLocationsValidationService() }
    single { TemporalHomesValidationService() }
    single { AuthValidationService() }
}

private fun getOrigins(config: AppConfig): List<String> {
    // Comma-separated string, not a HOCON list - see application.conf's
    // webauthn.origins comment for why.
    val originsList = config.propertyOrNull("webauthn.origins")?.getString()
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
    if (!originsList.isNullOrEmpty()) {
        return originsList
    }
    return listOf("http://localhost:80")
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
