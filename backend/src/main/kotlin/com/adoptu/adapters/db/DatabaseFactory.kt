package com.adoptu.adapters.db

import com.adoptu.config.AppConfig
import com.adoptu.dto.input.UserRole
import com.universaliun.ratelimit.backend.adapter.out.persistence.tables.RateLimitStateTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object DatabaseFactory {
    private val clock: Clock = Clock.System
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private var dataSource: HikariDataSource? = null

    val listOfTables = listOf(
        Users,
        EmailVerificationTokens,
        EmailVerificationAttempts,
        UserPasswords,
        MagicLinkTokens,
        UserActiveRoles,
        PendingRoleActivations,
        Photographers,
        WebAuthnCredentials,
        Pets,
        PetImages,
        AdoptionRequests,
        PhotographyRequests,
        TemporalHomes,
        BlockedRescuers,
        TemporalHomeRequests,
        AnimalShelters,
        SterilizationLocations,
        UserShelters,
        UserSterilizationLocations,
        PasswordResetTokens,
        EmailChangeTokens,
        ProfileEmailVerificationTokens,
        SpamReportTokens,
        LoginAttempts,
        CryptoKeys,
        WebAuthnChallenges,
        RateLimitStateTable)
    fun init(config: AppConfig) {
        val env = config.propertyOrNull("env")?.getString() ?: "prod"
        val prefix = "db.$env"

        val driverClassName = config.property("$prefix.postgres.driver").getString()
        var jdbcURL = config.property("$prefix.postgres.url").getString()
        val user = config.property("$prefix.postgres.user").getString()
        val password = config.property("$prefix.postgres.password").getString()

        logger.info("Postgres database url: $jdbcURL")
        if (!jdbcURL.startsWith("jdbc:")) {
            val parts = jdbcURL.split(":")
            val host = parts.getOrElse(0) { jdbcURL }
            val port = parts.getOrElse(1) { "5432" }
            jdbcURL = "jdbc:postgresql://$host:$port/adoptu"
        }

        // Database.connect(url, driver, user, password) opens a brand-new physical
        // connection (TCP handshake + auth + fresh Postgres backend process) via plain
        // DriverManager on every transaction{} call — there is no pooling at all. This was
        // measured as the single biggest throughput win available here (~6.7x), well ahead
        // of dispatcher/pool-sizing tuning below. Pool size is deliberately the SAME
        // PoolSizing.computeSize() value used by dbDispatcher (DbDispatcher.kt) — a mismatch
        // between the two just relocates the bottleneck instead of removing it.
        //
        // init() can be called more than once in the same JVM (e.g. DatabaseFactoryInitIT
        // calls it once per @Test against a fresh Testcontainers database). Close any
        // previous pool first — otherwise each call leaks a full pool's worth of open
        // connections, and enough repeated calls exhaust Postgres's max_connections.
        dataSource?.close()
        val poolSize = PoolSizing.computeSize()
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = jdbcURL
            this.driverClassName = driverClassName
            username = user
            this.password = password
            maximumPoolSize = poolSize
            // Postgres JDBC driver defaults to always re-preparing statements server-side;
            // these three enable client-side prepared statement caching so repeated queries
            // (every repository call re-runs the same fixed-shape SQL) skip re-parsing.
            addDataSourceProperty("prepareThreshold", "1")
            addDataSourceProperty("preparedStatementCacheQueries", "512")
            addDataSourceProperty("preparedStatementCacheSizeMiB", "10")
        })
        dataSource = ds
        Runtime.getRuntime().addShutdownHook(Thread { ds.close() })

        Database.connect(ds)

        transaction {
            SchemaUtils.create(*listOfTables.toTypedArray())
            SchemaUtils.addMissingColumnsStatements(*listOfTables.toTypedArray())
                .forEach { exec(it)}
            MigrationUtils.dropUnmappedColumnsStatements(*listOfTables.toTypedArray())
        }

        createDefaultAdmin(config)
    }

    private fun createDefaultAdmin(config: AppConfig) {
        val adminEmail = config.propertyOrNull("admin.email")?.getString() ?: "adopt-u@adopt-u.org"

        transaction {
            val existingAdmin = Users.selectAll().where { Users.username.eq(adminEmail) }.firstOrNull()
            if (existingAdmin == null) {
                val userId = Users.insert {
                    it[username] = adminEmail
                    it[displayName] = "Admin"
                    it[createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = userId
                    it[UserActiveRoles.role] = UserRole.ADMIN.name
                }
                
                logger.info("Default admin user created: $adminEmail")
            }
        }
    }
}
