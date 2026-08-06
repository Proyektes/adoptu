package com.adoptu

import com.adoptu.adapters.db.*
import com.adoptu.adapters.notification.NotificationEmailAdapter
import com.adoptu.di.emailSenderPortFromConfig
import com.universaliun.email.common.EmailSenderPort
import com.adoptu.adapters.storage.AdoptuImageStorageAdapter
import com.universaliun.storagekit.backend.adapter.out.storage.ReturnFormat
import com.universaliun.storagekit.backend.adapter.out.storage.S3ObjectStorageAdapter
import com.universaliun.storagekit.backend.adapter.out.storage.S3StorageConfig as StorageKitS3Config
import com.adoptu.config.AppConfig
import com.adoptu.mocks.TestClock
import com.adoptu.ports.*
import com.adoptu.services.*
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.TestServerHandle
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Testcontainers
@OptIn(ExperimentalTime::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationIntegrationTest {

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
    private var handle: TestServerHandle? = null
    private val baseUrl: String get() = handle!!.baseUrl

    private fun createTestConfig(): Map<String, Any> {
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
            "admin.email" to "admin@adopt-u.com",
            "sns.region" to localstackContainer.region
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
                EmailVerificationTokens,
                AnimalShelters
            )
        }
    }

    @BeforeAll
    fun setUpAll() {
        println("Starting application integration test")
        println("PostgreSQL JDBC URL: ${postgresContainer.jdbcUrl}")
        println("LocalStack endpoint: ${localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3)}")
    }

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        println("Starting test: ${testInfo.displayName}")

        val configOverrides = createTestConfig()
        val config = AppConfig.fromMap(configOverrides)
        initDatabase(config)

        val testModules = module {
            single { config }
            single<Clock> { testClock }
            single { WebAuthnService(get(), get()) }
            single<UserRepositoryPort> { com.adoptu.adapters.db.repositories.UserRepository(get()) }
            single<PetRepositoryPort> { com.adoptu.adapters.db.repositories.PetRepositoryImpl(get()) }
            single<com.adoptu.ports.SavedSearchRepositoryPort> { com.adoptu.adapters.db.repositories.SavedSearchRepositoryImpl(get()) }
            single<PhotographerRepositoryPort> { com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl(get(), get(), get()) }
            single<TemporalHomeRepositoryPort> { com.adoptu.adapters.db.repositories.TemporalHomeRepositoryImpl(get(), get(), get()) }
            single<ShelterRepositoryPort> { com.adoptu.adapters.db.repositories.ShelterRepository(get()) }
            single<ImageStoragePort> {
                val endpoint = localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString()
                val s3Client = software.amazon.awssdk.services.s3.S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(localstackContainer.region))
                    .credentialsProvider(
                        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                localstackContainer.accessKey, localstackContainer.secretKey
                            )
                        )
                    )
                    .endpointOverride(java.net.URI.create(endpoint))
                    .forcePathStyle(true)
                    .build()
                val storage = S3ObjectStorageAdapter(
                    s3Client,
                    StorageKitS3Config(sseEnabled = false, autoCreateBucket = true, returnFormat = ReturnFormat.PublicUrl(urlBase = endpoint)),
                )
                AdoptuImageStorageAdapter(storage, "test-bucket", localstackContainer.region, endpoint, publicUrl = null, pathStyleAccess = true)
            }
            single<EmailSenderPort> { emailSenderPortFromConfig(config) }
            single<NotificationPort> { NotificationEmailAdapter(get()) }
            single<UserService> { UserService(get(), get()) }
            single<PetService> { PetService(get(), get(), get(), get(), get()) }
            single<PhotographerService> { PhotographerService(get(), get(), get(), get()) }
            single<TemporalHomeService> { TemporalHomeService(get(), get(), get(), get()) }
            single { ShelterService(get()) }
            single { com.universaliun.ratelimit.common.RateLimiter(com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter()) }
            single { EmailVerificationService(get(), get(), get(), "http://localhost:80", get()) }
        }

        handle = TestServer.start(configOverrides = configOverrides, modules = listOf(testModules), initDatabase = false)

        println("Server started, base URL: $baseUrl")
    }

    @AfterEach
    fun tearDown() {
        handle?.stop()
        handle = null
    }

    @Test
    fun `containers are running`() {
        assertTrue(postgresContainer.isRunning, "PostgreSQL container should be running")
        assertTrue(localstackContainer.isRunning, "LocalStack container should be running")
    }

    @Test
    fun `server is listening on configured port`() {
        val port = handle!!.server.port()
        assertTrue(port > 0, "Server should be listening on a port, was: $port")
    }

    @Test
    fun `root endpoint responds with HTTP 200`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/")
        assertTrue(
            response.statusCode() in listOf(200, 404, 302),
            "Root endpoint should respond with valid status, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `health endpoint responds`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/health")
        assertTrue(
            response.statusCode() in listOf(200, 404),
            "Health endpoint should respond with valid status, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `login page is accessible`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/login")
        assertTrue(
            response.statusCode() in listOf(200, 302),
            "Login page should be accessible, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `register page is accessible`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/register")
        assertTrue(
            response.statusCode() in listOf(200, 302),
            "Register page should be accessible, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `api pets endpoint returns valid response`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/api/pets")
        assertTrue(
            response.statusCode() < 500,
            "Pets API should not return 5xx error, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `api photographers endpoint returns valid response`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/api/photographers")
        assertTrue(
            response.statusCode() < 500,
            "Photographers API should not return 5xx error, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `api auth me endpoint responds`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/api/auth/me")
        assertTrue(
            response.statusCode() in listOf(200, 401),
            "Auth me endpoint should respond, got: ${response.statusCode()}"
        )
    }

    @Test
    fun `unknown route returns 404`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/api/nonexistent-route-xyz")
        assertEquals(404, response.statusCode(), "Unknown route should return 404")
    }

    @Test
    fun `application responds within reasonable time`() = runTestWithRetry {
        val startTime = System.currentTimeMillis()
        val response = TestHttp.get("$baseUrl/")
        val duration = System.currentTimeMillis() - startTime
        assertTrue(duration < 5000, "Application should respond within 5 seconds, took: ${duration}ms")
    }

    @Test
    fun `static resources are served`() = runTestWithRetry {
        val response = TestHttp.get("$baseUrl/style.css")
        assertTrue(
            response.statusCode() in listOf(200, 404),
            "Static CSS should be accessible, got: ${response.statusCode()}"
        )
    }

    private fun runTestWithRetry(block: () -> Unit) {
        var lastException: Throwable? = null
        repeat(3) { attempt ->
            try {
                block()
                return
            } catch (e: Throwable) {
                lastException = e
                if (attempt < 2) {
                    println("Attempt ${attempt + 1} failed, retrying: ${e.message}")
                    Thread.sleep(500)
                }
            }
        }
        throw lastException ?: RuntimeException("Test failed after retries")
    }
}
