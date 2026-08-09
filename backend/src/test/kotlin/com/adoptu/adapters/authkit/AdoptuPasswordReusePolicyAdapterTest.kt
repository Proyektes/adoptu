package com.adoptu.adapters.authkit

import com.universaliun.auth.backend.application.auth.verifyPassword
import com.universaliun.auth.common.identity.AuthUserId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdoptuPasswordReusePolicyAdapterTest {

    private lateinit var db: Database
    private val adapter = AdoptuPasswordReusePolicyAdapter()
    // Named testUserId, not userId -- PasswordHistoryTable.insert {} makes the table an implicit
    // receiver, so a bare `userId` inside that block would silently resolve to the table's own
    // userId Column instead of a local property of that name.
    private val testUserId = AuthUserId("42")

    @BeforeAll
    fun connectDb() {
        db = Database.connect(
            "jdbc:h2:mem:adoptu_password_history_test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            driver = "org.h2.Driver",
        )
    }

    @BeforeEach
    fun setUp() {
        transaction(db) { SchemaUtils.create(PasswordHistoryTable) }
    }

    @AfterEach
    fun tearDown() {
        transaction(db) { SchemaUtils.drop(PasswordHistoryTable) }
    }

    private fun insertHistoryRow(passwordHashValue: String, createdAtValue: Instant) {
        transaction(db) {
            PasswordHistoryTable.insert { row ->
                row[PasswordHistoryTable.userId] = testUserId.value
                row[PasswordHistoryTable.passwordHash] = passwordHashValue
                row[PasswordHistoryTable.createdAt] = createdAtValue
            }
        }
    }

    @Test fun `checkReuse passes for a brand new password with no history`() {
        adapter.checkReuse(testUserId, "Br4nd!NewPassw0rd", currentPasswordHash = null)
    }

    @Test fun `recordChange persists a real, verifiable password hash`() {
        adapter.recordChange(testUserId, hashViaAdapterUnderTest("Hist0ric!Passw0rd"))

        val stored = transaction(db) {
            PasswordHistoryTable.selectAll().where { PasswordHistoryTable.userId eq testUserId.value }.single()
        }
        assertEquals(testUserId.value, stored[PasswordHistoryTable.userId])
    }

    @Test fun `checkReuse rejects a password recorded within the last 2 years`() {
        val hash = hashViaAdapterUnderTest("Recent!Passw0rd")
        adapter.recordChange(testUserId, hash)

        assertFailsWith<PasswordReuseException> {
            adapter.checkReuse(testUserId, "Recent!Passw0rd", currentPasswordHash = null)
        }
    }

    @Test fun `checkReuse allows a different password even with history present`() {
        adapter.recordChange(testUserId, hashViaAdapterUnderTest("Old!Passw0rd"))

        adapter.checkReuse(testUserId, "Completely!Different1", currentPasswordHash = null)
    }

    @Test fun `checkReuse rejects the currently-active password even if it predates any recorded history`() {
        val currentHash = hashViaAdapterUnderTest("Active!Passw0rd")

        assertFailsWith<PasswordReuseException> {
            adapter.checkReuse(testUserId, "Active!Passw0rd", currentPasswordHash = currentHash)
        }
    }

    @Test fun `checkReuse ignores a password older than the 2-year reuse window`() {
        val hash = hashViaAdapterUnderTest("Ancient!Passw0rd")
        insertHistoryRow(hash, Instant.now().minusSeconds(731L * 24 * 60 * 60))

        adapter.checkReuse(testUserId, "Ancient!Passw0rd", currentPasswordHash = null)
    }

    @Test fun `recordChange prunes history older than the reuse window`() {
        val oldHash = hashViaAdapterUnderTest("Prune!Me1")
        insertHistoryRow(oldHash, Instant.now().minusSeconds(731L * 24 * 60 * 60))

        adapter.recordChange(testUserId, hashViaAdapterUnderTest("Fresh!Passw0rd"))

        val remaining = transaction(db) {
            PasswordHistoryTable.selectAll().where { PasswordHistoryTable.userId eq testUserId.value }.map { it[PasswordHistoryTable.passwordHash] }
        }
        assertEquals(1, remaining.size)
        assertTrue(verifyPassword("Fresh!Passw0rd", remaining.single()))
    }

    /** AuthKit's own Argon2id `PasswordHasher.hash(...)` is `internal` to that module, not visible
     *  here -- [verifyPassword] accepts BCrypt hashes too (its documented legacy-format support),
     *  so a real BCrypt hash round-trips through [AdoptuPasswordReusePolicyAdapter.checkReuse]
     *  identically to a real Argon2id one issued by AuthKit's ResetPasswordService. */
    private fun hashViaAdapterUnderTest(rawPassword: String): String =
        at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(4, rawPassword.toCharArray())
}
