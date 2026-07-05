package com.adoptu.testsupport

import com.adoptu.adapters.db.DatabaseFactory
import com.adoptu.config.AppConfig
import com.adoptu.configureRouting
import com.adoptu.di.appModule
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.crypto.CryptoService
import com.adoptu.web.JsonSupport
import com.adoptu.web.setSession
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRouting
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

        // configureRouting() unconditionally mounts every route group, including authRoutes(),
        // which resolves AppConfig from Koin eagerly (not lazily per-request) to compute
        // adminEmail. A caller-supplied custom `modules` list built for one narrow route group
        // (the common pattern in the ported E2E tests) usually doesn't bind AppConfig - append a
        // fallback binding last so it's always resolvable, regardless of what the test needs.
        val effectiveModules = (modules ?: listOf(appModule(config))) + module { single { config } }
        startKoin { modules(effectiveModules) }
        try {
            if (initDatabase) {
                DatabaseFactory.init(config)
            }
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

private fun HttpRouting.Builder.registerTestLogin() {
    post("/test/login/{userId}", Handler { req, res ->
        val userId = req.path().pathParameters().get("userId").toInt()
        res.setSession(SessionUser(userId, "user$userId@test.com", "Test User $userId"))
        res.send("OK")
    })
}
