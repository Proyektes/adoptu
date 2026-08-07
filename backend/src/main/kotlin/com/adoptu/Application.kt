package com.adoptu

import com.adoptu.adapters.authkit.ADOPTU_RESOURCE_COUNT
import com.adoptu.adapters.authkit.AdoptuPasskeyCeremonyStoreAdapter
import com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuRefreshTokenRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter
import com.adoptu.adapters.authkit.AuthKitJwtKeyProvider
import com.adoptu.adapters.authkit.adoptuRoleByName
import com.adoptu.adapters.db.DatabaseFactory
import com.adoptu.config.AppConfig
import com.adoptu.di.appModule
import com.adoptu.routes.adminPetsRoutes
import com.adoptu.routes.adminShelterRoutes
import com.adoptu.routes.adminSterilizationLocationRoutes
import com.adoptu.routes.adminUsersRoutes
import com.adoptu.routes.authRoutes
import com.adoptu.routes.countryRoutes
import com.adoptu.routes.lostFoundRoutes
import com.adoptu.routes.savedSearchRoutes
import com.adoptu.routes.petEditSuggestionRoutes
import com.adoptu.routes.petFosterPlacementRoutes
import com.adoptu.routes.sponsorshipRoutes
import com.adoptu.routes.volunteerRoutes
import com.adoptu.routes.petMedicalEventRoutes
import com.adoptu.routes.petsRoutes
import com.adoptu.routes.photographerRoutes
import com.adoptu.routes.shelterRoutes
import com.adoptu.routes.sterilizationLocationRoutes
import com.adoptu.routes.temporalHomeRoutes
import com.adoptu.routes.urgentRescueRoutes
import com.adoptu.routes.userShelterRoutes
import com.adoptu.routes.userSterilizationLocationRoutes
import com.adoptu.routes.usersRoutes
import com.adoptu.services.MedicalReminderScheduler
import com.adoptu.services.crypto.CryptoService
import com.adoptu.web.AccessLogFilter
import com.adoptu.web.JsonSupport
import com.universaliun.auth.backend.infrastructure.authKoinModule
import com.universaliun.auth.backend.infrastructure.installJwtAuth
import com.universaliun.auth.backend.infrastructure.installSecurityHeaders
import com.universaliun.auth.backend.domain.port.out.RefreshTokenRepositoryPort
import com.universaliun.auth.backend.domain.port.out.TokenBlocklistPort
import com.universaliun.auth.backend.domain.port.out.TokenServicePort
import com.universaliun.auth.common.rbac.PermissionSet
import io.helidon.http.Status
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdoptU")

fun main() {
    val config = AppConfig.load()
    val env = config.propertyOrNull("env")?.getString() ?: "dev"
    logger.info("Starting Adopt-U application (Helidon Níma) in $env environment")

    // DatabaseFactory.init() must run before startKoin{} - AuthKitJwtKeyProvider.loadOrCreate()
    // below needs a live DB connection, and its result is passed as plain String args into
    // authKoinModule(...) at module-construction time (not lazily resolved via get()).
    DatabaseFactory.init(config)
    CryptoService.initialize()

    val (jwtPrivateKey, jwtPublicKey) = AuthKitJwtKeyProvider.loadOrCreate()
    val rpId = config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost"
    val rpName = config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption"
    val origins = config.propertyOrNull("webauthn.origins")?.getString()
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: setOf("http://localhost:8080")

    // Constructed directly rather than resolved via Koin's get() - these bridge adapters are
    // stateless (each call opens its own Exposed transaction() against the shared DB), and
    // authKoinModule(...) needs concrete instances at module-construction time, before Koin has
    // started. appModule(config) below registers its own singletons of the same classes for
    // AuthRoutes.kt's direct injection (has-passkey check, resend-activation token persistence) -
    // harmless duplication, not shared mutable state.
    val kitUserRepository = AdoptuUserRepositoryAdapter()
    val kitPasskeyCredentialRepository = AdoptuPasskeyCredentialRepositoryAdapter()
    val kitRefreshTokenRepository = AdoptuRefreshTokenRepositoryAdapter()
    val kitPasskeyCeremonyStore = AdoptuPasskeyCeremonyStoreAdapter()

    startKoin {
        slf4jLogger()
        modules(
            appModule(config),
            authKoinModule(
                jwtPrivateKey = jwtPrivateKey,
                jwtPublicKey = jwtPublicKey,
                resourceCount = ADOPTU_RESOURCE_COUNT,
                roleByName = adoptuRoleByName,
                // Adopt-u's own registration flow (AuthRoutes.kt's applyRoleSelection) grants
                // roles itself, through its own UserRepository - not through AuthKit's
                // RegisterService/FinishPasskeySignupService, which never touch
                // user_active_roles (see AdoptuUserRepositoryAdapter's save() doc comment). This
                // stays at empty/no-op; a freshly AuthKit-registered row simply starts with no
                // roles until Adopt-u's own post-registration step grants some.
                defaultPermissions = PermissionSet.empty(0),
                // Preserves the native MagicLinkService's real 5-minute expiry (see
                // magicLinkEmailContent's "This link will expire in 5 minutes" text) - AuthKit's
                // own default is 15 minutes, which would silently lengthen the window.
                magicLinkExpiryMs = 5 * 60 * 1000L,
                requireEmailVerification = true,
                webAuthnRpId = rpId,
                webAuthnRpName = rpName,
                webAuthnOrigins = origins,
                userRepository = kitUserRepository,
                passkeyCredentialRepository = kitPasskeyCredentialRepository,
                refreshTokenRepository = kitRefreshTokenRepository,
                passkeyCeremonyStore = kitPasskeyCeremonyStore,
            ),
        )
    }

    val port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080
    val server = WebServer.builder()
        .port(port)
        .mediaContext(JsonSupport.mediaContext())
        .routing(::configureRouting)
        .build()
        .start()

    logger.info("Adopt-U listening on port ${server.port()}")

    MedicalReminderScheduler.start(CoroutineScope(Dispatchers.IO), GlobalContext.get().get())
}

/**
 * Content-Security-Policy for [installSecurityHeaders] below -- kept exactly as it was under the
 * local `SecurityHeadersFilter` this migrated from (see git history for that file's own doc
 * comment, which explained each directive in more depth than fits here):
 *  - script-src 'none' -- the backend is JSON-API-only (page rendering moved to the static site);
 *    its one remaining HTML response (the magic-link-login cookie bounce) has no `<script>` tag.
 *  - style-src 'self' -- every inline style="..." in the (now-removed) page templates was already
 *    moved to a CSS class before this policy was first written.
 *  - img-src allows blob: (client-side photo compression via canvas) and this app's own two CDN
 *    hosts (static.adopt-u.org, dynamic.adopt-u.org -- the pet-photo bucket's public URL is a
 *    custom CNAME, not covered by the *.amazonaws.com wildcard) plus *.amazonaws.com generally.
 */
private val ADOPTU_CONTENT_SECURITY_POLICY = listOf(
    "default-src 'none'",
    "script-src 'none'",
    "style-src 'self'",
    "img-src 'self' data: blob: https://static.adopt-u.org https://dynamic.adopt-u.org https://*.amazonaws.com",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
    "frame-ancestors 'none'",
).joinToString("; ")

internal fun configureRouting(routing: HttpRouting.Builder) {
    routing.addFilter(AccessLogFilter())
    routing.installSecurityHeaders(contentSecurityPolicy = ADOPTU_CONTENT_SECURITY_POLICY)

    val koin = GlobalContext.get()
    routing.installJwtAuth(
        tokenService = koin.get<TokenServicePort>(),
        tokenBlocklist = koin.get<TokenBlocklistPort>(),
        resourceCount = ADOPTU_RESOURCE_COUNT,
        roleByName = adoptuRoleByName,
        cookieName = "adoptu_access_token",
    )

    routing.error(io.helidon.http.NotFoundException::class.java) { _, res, _ ->
        res.status(Status.NOT_FOUND_404).send()
    }
    routing.error(io.helidon.http.HttpException::class.java) { _, res, ex ->
        // Helidon's static content handler uses this to short-circuit conditional
        // requests (e.g. If-None-Match -> 304) - it carries its own intended status
        // and headers and must not fall through to the generic 500 handler below.
        ex.headers().forEach { res.header(it) }
        res.status(ex.status()).send()
    }
    routing.error(Throwable::class.java) { _, res, cause ->
        logger.error("Unhandled exception", cause)
        res.status(Status.INTERNAL_SERVER_ERROR_500).send()
    }

    routing.get("/health", Handler { _, res -> res.send(mapOf("status" to "ok")) })

    routing.authRoutes()
    routing.countryRoutes()
    routing.petsRoutes()
    routing.petMedicalEventRoutes()
    routing.petFosterPlacementRoutes()
    routing.petEditSuggestionRoutes()
    routing.sponsorshipRoutes()
    routing.adminPetsRoutes()
    routing.usersRoutes()
    routing.adminUsersRoutes()
    routing.volunteerRoutes()
    routing.photographerRoutes()
    routing.temporalHomeRoutes()
    routing.urgentRescueRoutes()
    routing.lostFoundRoutes()
    routing.savedSearchRoutes()
    routing.shelterRoutes()
    routing.adminShelterRoutes()
    routing.sterilizationLocationRoutes()
    routing.adminSterilizationLocationRoutes()
    routing.userShelterRoutes()
    routing.userSterilizationLocationRoutes()
}
