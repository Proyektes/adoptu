package com.adoptu

import com.adoptu.adapters.authkit.AdoptuPasskeyCeremonyStoreAdapter
import com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuRefreshTokenRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter
import com.adoptu.adapters.authkit.AuthKitJwtKeyProvider
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
import com.adoptu.routes.petsRoutes
import com.adoptu.routes.photographerRoutes
import com.adoptu.routes.shelterRoutes
import com.adoptu.routes.sterilizationLocationRoutes
import com.adoptu.routes.temporalHomeRoutes
import com.adoptu.routes.urgentRescueRoutes
import com.adoptu.routes.userShelterRoutes
import com.adoptu.routes.userSterilizationLocationRoutes
import com.adoptu.routes.usersRoutes
import com.adoptu.services.crypto.CryptoService
import com.adoptu.web.AccessLogFilter
import com.adoptu.web.JsonSupport
import com.adoptu.web.SecurityHeadersFilter
import com.universaliun.auth.backend.infrastructure.authKoinModule
import com.universaliun.auth.backend.infrastructure.installJwtAuth
import com.universaliun.auth.backend.domain.port.out.RefreshTokenRepositoryPort
import com.universaliun.auth.backend.domain.port.out.TokenBlocklistPort
import com.universaliun.auth.backend.domain.port.out.TokenServicePort
import com.universaliun.auth.common.rbac.PermissionSet
import io.helidon.http.Status
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRouting
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
                // Adopt-u has no AuthKit-native RBAC (roles/permissions are handled entirely by
                // its own UserRepository/UserService, unrelated to AuthKit's JWT claims) - no
                // resources to enumerate, no role lookup to perform.
                resourceCount = 0,
                roleByName = { null },
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
}

internal fun configureRouting(routing: HttpRouting.Builder) {
    routing.addFilter(AccessLogFilter())
    routing.addFilter(SecurityHeadersFilter())

    val koin = GlobalContext.get()
    routing.installJwtAuth(
        tokenService = koin.get<TokenServicePort>(),
        tokenBlocklist = koin.get<TokenBlocklistPort>(),
        resourceCount = 0,
        roleByName = { null },
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
    routing.adminPetsRoutes()
    routing.usersRoutes()
    routing.adminUsersRoutes()
    routing.photographerRoutes()
    routing.temporalHomeRoutes()
    routing.urgentRescueRoutes()
    routing.lostFoundRoutes()
    routing.shelterRoutes()
    routing.adminShelterRoutes()
    routing.sterilizationLocationRoutes()
    routing.adminSterilizationLocationRoutes()
    routing.userShelterRoutes()
    routing.userSterilizationLocationRoutes()
}
