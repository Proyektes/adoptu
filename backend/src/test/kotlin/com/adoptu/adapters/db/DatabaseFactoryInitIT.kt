package com.adoptu.adapters.db

import com.adoptu.config.AppConfig
import com.adoptu.dto.input.UserRole
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.Driver
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseFactoryInitIT {

    private var postgresContainer: PostgreSQLContainer<*>? = null

    @BeforeAll
    fun startContainer() {
        postgresContainer = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("postgres")
            .withUsername("testuser")
            .withPassword("testpassword")
            .withExposedPorts(5432)

        postgresContainer!!.start()

        val host = postgresContainer!!.host
        val port = postgresContainer!!.firstMappedPort

        val driver = Driver()
        val props = Properties().apply {
            setProperty("user", "testuser")
            setProperty("password", "testpassword")
        }

        val conn: java.sql.Connection = driver.connect("jdbc:postgresql://$host:$port/postgres", props)!!
        conn.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE DATABASE adoptu_test1")
                stmt.execute("CREATE DATABASE adoptu_test2")
                stmt.execute("CREATE DATABASE adoptu_test3")
                stmt.execute("CREATE DATABASE adoptu_test4")
                stmt.execute("CREATE DATABASE adoptu_test5")
                stmt.execute("CREATE DATABASE adoptu_test6")
                stmt.execute("CREATE DATABASE adoptu_test7")
            }
        }
    }

    @AfterAll
    fun stopContainer() {
        postgresContainer?.stop()
    }

    private fun createConfig(databaseName: String, env: String = "prod", adminEmail: String? = null): AppConfig {
        val values = mutableMapOf<String, Any>(
            "env" to env,
            "db.$env.postgres.driver" to "org.postgresql.Driver",
            "db.$env.postgres.url" to "jdbc:postgresql://${postgresContainer!!.host}:${postgresContainer!!.firstMappedPort}/$databaseName",
            "db.$env.postgres.user" to (postgresContainer?.username ?: "testuser"),
            "db.$env.postgres.password" to (postgresContainer?.password ?: "testpassword")
        )
        if (adminEmail != null) {
            values["admin.email"] = adminEmail
        }
        return AppConfig.fromMap(values)
    }

    @Test
    fun `init creates all tables and default admin with default email`() {
        val config = createConfig("adoptu_test1")

        DatabaseFactory.init(config)

        transaction {
            val admin = Users.selectAll().where { Users.username.eq("adopt-u@adopt-u.org") }.firstOrNull()
            assertNotNull(admin, "Default admin should be created")
            assertEquals("Admin", admin[Users.displayName])
            assertTrue(admin[Users.createdAt] > 0)

            val roles = UserActiveRoles.selectAll().where { UserActiveRoles.userId.eq(admin[Users.id]) }.toList()
            assertEquals(1, roles.size, "Admin should have exactly one role")
            assertEquals(UserRole.ADMIN.name, roles[0][UserActiveRoles.role])
        }
    }

    @Test
    fun `init creates all tables and default admin with custom email`() {
        val config = createConfig("adoptu_test2", adminEmail = "custom-admin@test.com")

        DatabaseFactory.init(config)

        transaction {
            val admin = Users.selectAll().where { Users.username.eq("custom-admin@test.com") }.firstOrNull()
            assertNotNull(admin, "Custom admin should be created")
            assertEquals("Admin", admin[Users.displayName])
        }
    }

    @Test
    fun `init does not duplicate admin if already exists`() {
        val dbName = "adoptu_test3"

        val config = createConfig(dbName)
        DatabaseFactory.init(config)

        val adminCountBefore = transaction {
            Users.selectAll().where { Users.username.eq("adopt-u@adopt-u.org") }.count()
        }

        assertEquals(1, adminCountBefore, "Admin should be created on first init")

        DatabaseFactory.init(config)

        val adminCountAfter = transaction {
            Users.selectAll().where { Users.username.eq("adopt-u@adopt-u.org") }.count()
        }

        assertEquals(1, adminCountAfter, "Should not duplicate admin on second init")
    }

    @Test
    fun `init uses dev environment when specified`() {
        val config = createConfig("adoptu_test4", env = "dev")

        DatabaseFactory.init(config)

        transaction {
            val userCount = Users.selectAll().count()
            assertTrue(userCount >= 0)
        }
    }

    @Test
    fun `init creates all required tables`() {
        val config = createConfig("adoptu_test5")

        DatabaseFactory.init(config)

        transaction {
            assertTrue(Users.selectAll().count() >= 0)
            assertTrue(UserActiveRoles.selectAll().count() >= 0)
            assertTrue(Photographers.selectAll().count() >= 0)
            assertTrue(WebAuthnCredentials.selectAll().count() >= 0)
            assertTrue(Pets.selectAll().count() >= 0)
            assertTrue(PetImages.selectAll().count() >= 0)
            assertTrue(AdoptionRequests.selectAll().count() >= 0)
            assertTrue(PhotographyRequests.selectAll().count() >= 0)
            assertTrue(TemporalHomes.selectAll().count() >= 0)
            assertTrue(BlockedRescuers.selectAll().count() >= 0)
            assertTrue(TemporalHomeRequests.selectAll().count() >= 0)
        }
    }

    @Test
    fun `init sets correct timestamp on admin user`() {
        val beforeInit = Clock.System.now().toEpochMilliseconds()
        val config = createConfig("adoptu_test6")

        DatabaseFactory.init(config)

        val afterInit = Clock.System.now().toEpochMilliseconds()

        transaction {
            val admin = Users.selectAll().where { Users.username.eq("adopt-u@adopt-u.org") }.firstOrNull()
            assertNotNull(admin)
            val createdAt = admin[Users.createdAt]
            assertTrue(createdAt >= beforeInit, "Admin createdAt should be >= beforeInit")
            assertTrue(createdAt <= afterInit, "Admin createdAt should be <= afterInit")
        }
    }

    @Test
    fun `init assigns ADMIN role to new admin user`() {
        val config = createConfig("adoptu_test7")

        DatabaseFactory.init(config)

        transaction {
            val admin = Users.selectAll().where { Users.username.eq("adopt-u@adopt-u.org") }.firstOrNull()
            assertNotNull(admin)

            val roles = UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId.eq(admin[Users.id]) }
                .toList()

            assertEquals(1, roles.size, "Admin should have exactly one role")
            assertEquals(UserRole.ADMIN.name, roles[0][UserActiveRoles.role], "Role should be ADMIN")
        }
    }
}