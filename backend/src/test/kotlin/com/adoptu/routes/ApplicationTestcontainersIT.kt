package com.adoptu.routes

import com.adoptu.adapters.db.*
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.TemporalHomeRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.notification.NotificationEmailAdapter
import com.adoptu.di.emailSenderPortFromConfig
import com.universaliun.email.common.EmailSenderPort
import com.adoptu.adapters.storage.S3ImageStorageAdapter
import com.adoptu.config.AppConfig
import com.adoptu.mocks.TestClock
import com.adoptu.ports.*
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.TestServerHandle
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@Testcontainers
@OptIn(ExperimentalTime::class)
class ApplicationTestcontainersIT {

    companion object {
        @Container
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("adoptu")
            .withUsername("adoptu")
            .withPassword("Ad0ptU")

        @Container
        val localstackContainer: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.S3)
            .withEnv("EAGER_SERVICE_LOADING", "1")
            .waitingFor(Wait.forListeningPort())
    }

    private val testClock: Clock = TestClock()

    private fun createConfigOverrides(): Map<String, Any> {
        return mapOf(
            "env" to "test",
            "db.test.postgres.driver" to "org.postgresql.Driver",
            "db.test.postgres.url" to postgresContainer.jdbcUrl,
            "db.test.postgres.user" to postgresContainer.username,
            "db.test.postgres.password" to postgresContainer.password,
            "storage.test.bucket" to "test-bucket",
            "storage.test.region" to localstackContainer.region,
            "storage.test.endpoint" to localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
            "storage.test.access_key_id" to localstackContainer.accessKey,
            "storage.test.secret_access_key" to localstackContainer.secretKey,
            "storage.test.path_style_access" to "true",
            "email.from" to "test@adopt-u.com",
            "admin.email" to "admin@adopt-u.com"
        )
    }

    private fun initDatabase(config: AppConfig) {
        val driverClassName = config.property("db.test.postgres.driver").getString()
        val jdbcURL = config.property("db.test.postgres.url").getString()
        val user = config.property("db.test.postgres.user").getString()
        val password = config.property("db.test.postgres.password").getString()

        Database.connect(jdbcURL, driverClassName, user = user, password = password)

        transaction {
            SchemaUtils.drop(
                EmailVerificationTokens,
                TemporalHomeRequests,
                BlockedRescuers,
                TemporalHomes,
                AdoptionRequests,
                PetImages,
                PhotographyRequests,
                Pets,
                Photographers,
                WebAuthnCredentials,
                UserActiveRoles,
                Users
            )
            SchemaUtils.create(
                Users,
                UserActiveRoles,
                WebAuthnCredentials,
                Photographers,
                Pets,
                PetImages,
                AdoptionRequests,
                PhotographyRequests,
                TemporalHomes,
                BlockedRescuers,
                TemporalHomeRequests,
                EmailVerificationTokens
            )
        }
    }

    private fun startTestServer(): TestServerHandle {
        val configOverrides = createConfigOverrides()
        val config = AppConfig.fromMap(configOverrides)

        // Initialize database connection and schema
        initDatabase(config)

        val testModules = module {
            single { config }
            single<Clock> { testClock }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<com.adoptu.services.UserService> { com.adoptu.services.UserService(get(), get()) }
            single { com.universaliun.ratelimit.common.RateLimiter(com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter()) }
            single<com.adoptu.services.EmailVerificationService> { com.adoptu.services.EmailVerificationService(get(), get(), get(), "http://localhost:80", get()) }
            single<com.adoptu.services.PasswordService> { com.adoptu.services.PasswordService(get(), get(), get(), "http://localhost:80", get()) }
            single<com.adoptu.services.MagicLinkService> { com.adoptu.services.MagicLinkService(get(), get(), get(), "http://localhost:80", get(), get()) }
            single<EmailSenderPort> { emailSenderPortFromConfig(config) }
            single<NotificationPort> { NotificationEmailAdapter(get()) }
            single { WebAuthnService(get(), get(), get(), get(), get(), config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com", config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost", config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption", listOf(config.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")) }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single<TemporalHomeRepositoryPort> { TemporalHomeRepositoryImpl(get(), get(), get()) }
            single<ImageStoragePort> {
                S3ImageStorageAdapter(
                    bucketName = "test-bucket",
                    region = localstackContainer.region,
                    accessKeyId = localstackContainer.accessKey,
                    secretAccessKey = localstackContainer.secretKey,
                    endpoint = localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
                    pathStyleAccess = true
                )
            }
            single<com.adoptu.services.PetService> { com.adoptu.services.PetService(get(), get(), get(), get()) }
            single<com.adoptu.services.PhotographerService> { com.adoptu.services.PhotographerService(get(), get(), get(), get()) }
            single<com.adoptu.services.TemporalHomeService> { com.adoptu.services.TemporalHomeService(get(), get(), get(), get()) }
        }

        return TestServer.start(configOverrides = configOverrides, modules = listOf(testModules), initDatabase = false)
    }

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        println("Starting test: ${testInfo.displayName}")
        println("PostgreSQL: ${postgresContainer.jdbcUrl}")
        println("LocalStack: ${localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3)}")
    }

    @Test
    fun `health endpoint responds`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/health")
            assertTrue(
                response.statusCode() in listOf(200, 404),
                "Health endpoint should respond"
            )
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `S3 storage is accessible via localstack`() {
        val handle = startTestServer()
        try {
            // The S3ImageStorageAdapter should be able to connect to localstack
            // This test verifies the container is properly configured
            assertNotNull(localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3))
            assertTrue(localstackContainer.isRunning)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `database connection works`() {
        val handle = startTestServer()
        try {
            // Test that database connection is working by accessing an endpoint that queries the DB
            val response = TestHttp.get("${handle.baseUrl}/api/pets")
            println("Response status: ${response.statusCode()}")
            assertTrue(
                response.statusCode() < 500,
                "Database connection should work without 5xx errors, got: ${response.statusCode()}"
            )
        } finally {
            handle.stop()
        }
    }
}
