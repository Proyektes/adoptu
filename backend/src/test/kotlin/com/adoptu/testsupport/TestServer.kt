package com.adoptu.testsupport

import com.adoptu.adapters.authkit.AdoptuPasskeyCeremonyStoreAdapter
import com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuRefreshTokenRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter
import com.adoptu.adapters.authkit.AuthKitJwtKeyProvider
import com.adoptu.adapters.db.DatabaseFactory
import com.adoptu.config.AppConfig
import com.adoptu.configureRouting
import com.adoptu.di.appModule
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.crypto.CryptoService
import com.adoptu.web.JsonSupport
import com.adoptu.web.setSession
import com.universaliun.auth.backend.domain.port.out.TokenServicePort
import com.universaliun.auth.backend.domain.port.out.UserRepositoryPort
import com.universaliun.auth.backend.infrastructure.authKoinModule
import com.universaliun.auth.common.identity.AuthUserId
import com.universaliun.auth.common.rbac.PermissionSet
import io.helidon.http.SetCookie
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRouting
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Replaces Ktor's `testApplication { ... }` / `embeddedServer(Netty, ...)` test bootstrapping.
 * Starts a real Helidon Níma WebServer on a random port, wired through the same
 * `configureRouting` production code path as Application.kt, so tests exercise real routing.
 */
class TestServerHandle(val server: WebServer, val baseUrl: String) {
    fun stop() {
        server.stop()
        stopKoin()
    }
}

private const val DEFAULT_TEST_CONFIG_ENV = "test"

fun defaultTestConfig(dbName: String): Map<String, Any> = mapOf(
    "env" to DEFAULT_TEST_CONFIG_ENV,
    "db.test.postgres.driver" to "org.h2.Driver",
    "db.test.postgres.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "db.test.postgres.user" to "sa",
    "db.test.postgres.password" to "",
    "storage.test.bucket" to "test-bucket",
    "storage.test.region" to "us-east-1",
    "storage.test.endpoint" to "",
    "storage.test.path_style_access" to "false",
    "email.from" to "test@test.com",
    "admin.email" to "admin@adopt-u.com"
)

object TestServer {
    private var dbCounter = 0

    /**
     * @param configOverrides merged over [defaultTestConfig]; pass "env" etc to change the H2 db.
     * @param modules Koin modules to load INSTEAD of the production `appModule(config)` - use
     *   this when a test needs mocked adapters (e.g. MockImageStorage, MockNotificationAdapter)
     *   like the old Ktor E2E tests' inline `module { ... }` blocks did.
     * @param initDatabase when true (default), calls `DatabaseFactory.init(config)` to create
     *   the HikariCP-pooled H2 connection and schema. Set false when the test manages its own
     *   connection via `TestDatabase.initH2()`/`clearAllData()` instead (Exposed's
     *   `TransactionManager.defaultDatabase` is process-global, so only one should run per test).
     * @param withTestLogin also registers `POST /test/login/{userId}`, setting a real signed
     *   session cookie for that user id without going through the production login flow -
     *   mirrors the old Ktor tests' `client.loginAs(userId)` helper.
     */
    fun start(
        configOverrides: Map<String, Any> = emptyMap(),
        modules: List<Module>? = null,
        initDatabase: Boolean = true,
        withTestLogin: Boolean = false
    ): TestServerHandle {
        val dbName = "testdb_${System.identityHashCode(this)}_${dbCounter++}_${System.nanoTime()}"
        val config = AppConfig.fromMap(defaultTestConfig(dbName) + configOverrides)

        // DB must be ready before AuthKitJwtKeyProvider.loadOrCreate() below (mirrors
        // Application.kt's main() ordering fix - loadOrCreate() needs a live connection and its
        // result is passed as plain String args into authKoinModule(...) at module-construction
        // time). When initDatabase is false, the caller's own TestDatabase.initH2() already ran
        // in @BeforeEach before start() was invoked, so the DB is ready either way.
        if (initDatabase) {
            DatabaseFactory.init(config)
        }

        // configureRouting() unconditionally installs installJwtAuth (needs TokenServicePort/
        // TokenBlocklistPort) and mounts authRoutes(), which resolves AppConfig from Koin eagerly
        // (not lazily per-request) to compute adminEmail. A caller-supplied custom `modules` list
        // built for one narrow route group (the common pattern in the ported E2E tests) usually
        // binds neither - provide authKoinModule(...) and a config fallback so both are always
        // resolvable regardless of what the test's own module list provides.
        val (jwtPrivateKey, jwtPublicKey) = AuthKitJwtKeyProvider.loadOrCreate()
        val authModule = authKoinModule(
            jwtPrivateKey = jwtPrivateKey,
            jwtPublicKey = jwtPublicKey,
            // Adopt-u has no AuthKit-native RBAC - see Application.kt's main() for the same choice.
            resourceCount = 0,
            roleByName = { null },
            defaultPermissions = PermissionSet.empty(0),
            magicLinkExpiryMs = 5 * 60 * 1000L,
            requireEmailVerification = true,
            webAuthnRpId = "localhost",
            webAuthnRpName = "Adopt-U Pet Adoption",
            webAuthnOrigins = setOf("http://localhost:8080"),
            userRepository = AdoptuUserRepositoryAdapter(),
            passkeyCredentialRepository = AdoptuPasskeyCredentialRepositoryAdapter(),
            refreshTokenRepository = AdoptuRefreshTokenRepositoryAdapter(),
            passkeyCeremonyStore = AdoptuPasskeyCeremonyStoreAdapter(),
        )
        // Koin's later-registered definition silently wins on a type collision (no error) - the
        // config fallback and authModule MUST come first, so a caller-supplied `modules` list that
        // binds its own AppConfig (e.g. to override admin.email for a test) or its own AuthKit
        // port overrides (rare, but authKoinModule's own defaults would otherwise win instead)
        // takes precedence, not the other way around. This bit a real test once already: a test
        // binding its own admin.email got silently overridden by this fallback's default value
        // when the fallback was appended last instead of first.
        val effectiveModules = listOf(module { single { config } }, authModule) + (modules ?: listOf(appModule(config)))
        startKoin { modules(effectiveModules) }
        try {
            CryptoService.initialize()

            val server = WebServer.builder()
                .port(0)
                .mediaContext(JsonSupport.mediaContext())
                .routing { routing ->
                    configureRouting(routing)
                    if (withTestLogin) {
                        routing.registerTestLogin()
                    }
                }
                .build()
                .start()

            return TestServerHandle(server, "http://localhost:${server.port()}")
        } catch (e: Exception) {
            // Startup failed after Koin was already started globally - clean it up so the next
            // test's startKoin() doesn't fail with KoinApplicationAlreadyStartedException and
            // mask the real error behind an unrelated cascade of failures.
            stopKoin()
            throw e
        }
    }
}

// Mirrors the old Ktor tests' `client.loginAs(userId)` helper. Route groups other than
// AuthRoutes.kt (Pets/Users/Photographer/... ) haven't been migrated onto AuthKit yet and still
// authenticate via the native `req.getSession()` cookie -- that native SessionUser cookie MUST
// keep being set here unconditionally, or every one of those still-native suites' test-logins
// break (confirmed: an earlier version of this helper that set *only* the AuthKit cookie turned
// 256 tests red across PetsRoutesE2ETest/UsersRoutesE2ETest/etc.). AuthRoutes.kt itself now
// authenticates via `req.currentPrincipal()`, populated by AuthKit's JwtAuthFilter from a JWT in
// the "adoptu_access_token" cookie (see ACCESS_COOKIE in AuthRoutes.kt) -- so also mint a real
// AuthKit access token via the same TokenServicePort/UserRepositoryPort AuthRoutes.kt resolves
// from Koin (both bound by authKoinModule(...) above) and set it as a second cookie, best-effort:
// some callers deliberately log in as a userId with no AuthKit-visible row (e.g. to exercise a
// downstream 404), which must still reach the route rather than fail here, so this only adds the
// AuthKit cookie when the user actually resolves.
private fun HttpRouting.Builder.registerTestLogin() {
    post("/test/login/{userId}", Handler { req, res ->
        val userId = req.path().pathParameters().get("userId").toInt()
        res.setSession(SessionUser(userId, "user$userId@test.com", "Test User $userId"))
        val koin = GlobalContext.get()
        val user = koin.get<UserRepositoryPort>().findById(AuthUserId(userId.toString()))
        if (user != null) {
            val accessToken = koin.get<TokenServicePort>().generateAccessToken(user)
            res.headers().addCookie(
                SetCookie.builder("adoptu_access_token", accessToken).path("/").build()
            )
        }
        res.send("OK")
    })
}
