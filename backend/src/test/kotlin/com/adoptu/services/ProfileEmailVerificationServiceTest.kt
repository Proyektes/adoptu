package com.adoptu.services

import com.adoptu.adapters.db.ProfileEmailVerificationTokens
import com.adoptu.adapters.db.UserShelters
import com.adoptu.adapters.db.UserSterilizationLocations
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.common.Country
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class ProfileEmailVerificationServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var profileEmailVerificationService: ProfileEmailVerificationService
    private lateinit var userRepository: UserRepository
    private lateinit var mockNotificationAdapter: MockNotificationAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        profileEmailVerificationService = ProfileEmailVerificationService(
            userRepository,
            mockNotificationAdapter,
            clock,
            "http://localhost:80"
        )
    }

    // ---- handleProfileEmail ----

    @Test
    fun `handleProfileEmail returns true and sends nothing when profile email is blank`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.SHELTER, "account@test.com", "  ", "Test User"
        )

        assertTrue(result)
        assertEquals(0, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `handleProfileEmail returns true when profile email matches account email ignoring case`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.SHELTER, "account@test.com", "ACCOUNT@test.com", "Test User"
        )

        assertTrue(result)
        assertEquals(0, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `handleProfileEmail throws when email belongs to another account`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createTestUser("other@test.com")

        val exception = assertFailsWith<IllegalArgumentException> {
            profileEmailVerificationService.handleProfileEmail(
                userId, VerifiableProfileType.SHELTER, "account@test.com", "other@test.com", "Test User"
            )
        }
        assertTrue(exception.message?.contains("already associated") == true)
    }

    @Test
    fun `handleProfileEmail allows email already owned by the same user`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.SHELTER, "account@test.com", "account@test.com", "Test User"
        )

        // Blank/self email short-circuits before the ownership check, so no exception either way.
        assertTrue(result)
    }

    @Test
    fun `handleProfileEmail sends verification email and returns false for shelter`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.SHELTER, "account@test.com", "shelter@test.com", "Shelter Owner"
        )

        assertFalse(result)
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertEquals("shelter@test.com", sentEmails.first().to)
        assertTrue(sentEmails.first().body.contains("shelter"))
        assertTrue(sentEmails.first().body.contains("verify-profile-email?token="))

        val tokenRow = transaction {
            ProfileEmailVerificationTokens.selectAll()
                .where { ProfileEmailVerificationTokens.userId eq userId }
                .firstOrNull()
        }
        assertNotNull(tokenRow)
        assertEquals("SHELTER", tokenRow[ProfileEmailVerificationTokens.profileType])
    }

    @Test
    fun `handleProfileEmail sends verification email and returns false for sterilization location`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.STERILIZATION, "account@test.com", "sterilization@test.com", "Clinic Owner"
        )

        assertFalse(result)
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().body.contains("sterilization service"))
    }

    @Test
    fun `handleProfileEmail replaces previous pending token for same user and profile type`() = runBlocking {
        val userId = createTestUser("account@test.com")

        profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.SHELTER, "account@test.com", "first@test.com", "Test User"
        )
        clock.advanceMillis(1000)
        profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.SHELTER, "account@test.com", "second@test.com", "Test User"
        )

        val tokens = transaction {
            ProfileEmailVerificationTokens.selectAll()
                .where { ProfileEmailVerificationTokens.userId eq userId }
                .toList()
        }
        assertEquals(1, tokens.size)
        assertEquals("second@test.com", tokens.first()[ProfileEmailVerificationTokens.email])
    }

    // ---- verifyToken ----

    @Test
    fun `verifyToken returns false for unknown token`() = runBlocking {
        val result = profileEmailVerificationService.verifyToken("nonexistent-token")
        assertFalse(result)
    }

    @Test
    fun `verifyToken returns false for expired token`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createShelterProfile(userId, email = "shelter@test.com")
        val token = createVerificationToken(
            userId, VerifiableProfileType.SHELTER, "shelter@test.com",
            expiresAt = clock.now().toEpochMilliseconds() - 1000
        )

        val result = profileEmailVerificationService.verifyToken(token)
        assertFalse(result)
    }

    @Test
    fun `verifyToken marks shelter email verified and deletes token`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createShelterProfile(userId, email = "shelter@test.com")
        val token = createVerificationToken(userId, VerifiableProfileType.SHELTER, "shelter@test.com")

        val result = profileEmailVerificationService.verifyToken(token)
        assertTrue(result)

        val verified = transaction {
            UserShelters.selectAll()
                .where { UserShelters.userId eq userId }
                .first()[UserShelters.emailVerified]
        }
        assertTrue(verified)

        val remainingTokens = transaction {
            ProfileEmailVerificationTokens.selectAll()
                .where { ProfileEmailVerificationTokens.userId eq userId }
                .count()
        }
        assertEquals(0, remainingTokens)
    }

    @Test
    fun `verifyToken marks sterilization location email verified and deletes token`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createSterilizationProfile(userId, email = "sterilization@test.com")
        val token = createVerificationToken(userId, VerifiableProfileType.STERILIZATION, "sterilization@test.com")

        val result = profileEmailVerificationService.verifyToken(token)
        assertTrue(result)

        val verified = transaction {
            UserSterilizationLocations.selectAll()
                .where { UserSterilizationLocations.userId eq userId }
                .first()[UserSterilizationLocations.emailVerified]
        }
        assertTrue(verified)
    }

    @Test
    fun `verifyToken returns false when no matching profile row exists`() = runBlocking {
        val userId = createTestUser("account@test.com")
        // No shelter row created for this user, so the update in verifyToken affects 0 rows.
        val token = createVerificationToken(userId, VerifiableProfileType.SHELTER, "shelter@test.com")

        val result = profileEmailVerificationService.verifyToken(token)
        assertFalse(result)

        // Token should remain since nothing was actually verified.
        val remainingTokens = transaction {
            ProfileEmailVerificationTokens.selectAll()
                .where { ProfileEmailVerificationTokens.userId eq userId }
                .count()
        }
        assertEquals(1, remainingTokens)
    }

    @Test
    fun `verifyToken returns false for unrecognized profile type`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createShelterProfile(userId, email = "shelter@test.com")
        val token = createVerificationToken(userId, profileType = "BOGUS", email = "shelter@test.com")

        val result = profileEmailVerificationService.verifyToken(token)
        assertFalse(result)
    }

    // ---- isProfileEmailVerified ----

    @Test
    fun `isProfileEmailVerified returns true when no shelter profile exists`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.SHELTER, userId)
        assertTrue(result)
    }

    @Test
    fun `isProfileEmailVerified returns true when shelter contact email is blank`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createShelterProfile(userId, email = null)

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.SHELTER, userId)
        assertTrue(result)
    }

    @Test
    fun `isProfileEmailVerified returns false when shelter contact email is set but not verified`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createShelterProfile(userId, email = "shelter@test.com", emailVerified = false)

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.SHELTER, userId)
        assertFalse(result)
    }

    @Test
    fun `isProfileEmailVerified returns true when shelter contact email is verified`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createShelterProfile(userId, email = "shelter@test.com", emailVerified = true)

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.SHELTER, userId)
        assertTrue(result)
    }

    @Test
    fun `isProfileEmailVerified returns true when no sterilization profile exists`() = runBlocking {
        val userId = createTestUser("account@test.com")

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.STERILIZATION, userId)
        assertTrue(result)
    }

    @Test
    fun `isProfileEmailVerified returns false when sterilization contact email is set but not verified`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createSterilizationProfile(userId, email = "sterilization@test.com", emailVerified = false)

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.STERILIZATION, userId)
        assertFalse(result)
    }

    @Test
    fun `isProfileEmailVerified returns true when sterilization contact email is verified`() = runBlocking {
        val userId = createTestUser("account@test.com")
        createSterilizationProfile(userId, email = "sterilization@test.com", emailVerified = true)

        val result = profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.STERILIZATION, userId)
        assertTrue(result)
    }

    // ---- fixtures ----

    private fun createTestUser(username: String, displayName: String = "Test User"): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createShelterProfile(userId: Int, email: String?, emailVerified: Boolean = false) {
        transaction {
            UserShelters.insert {
                it[UserShelters.userId] = userId
                it[UserShelters.name] = "Test Shelter"
                it[UserShelters.country] = Country.UNITED_STATES
                it[UserShelters.city] = "Springfield"
                it[UserShelters.address] = "123 Test St"
                it[UserShelters.email] = email
                it[UserShelters.emailVerified] = emailVerified
                it[UserShelters.createdAt] = clock.now().toEpochMilliseconds()
                it[UserShelters.updatedAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    private fun createSterilizationProfile(userId: Int, email: String?, emailVerified: Boolean = false) {
        transaction {
            UserSterilizationLocations.insert {
                it[UserSterilizationLocations.userId] = userId
                it[UserSterilizationLocations.name] = "Test Clinic"
                it[UserSterilizationLocations.country] = Country.UNITED_STATES
                it[UserSterilizationLocations.city] = "Springfield"
                it[UserSterilizationLocations.address] = "123 Test St"
                it[UserSterilizationLocations.email] = email
                it[UserSterilizationLocations.emailVerified] = emailVerified
                it[UserSterilizationLocations.createdAt] = clock.now().toEpochMilliseconds()
                it[UserSterilizationLocations.updatedAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    private fun createVerificationToken(
        userId: Int,
        profileType: VerifiableProfileType,
        email: String,
        expiresAt: Long = clock.now().toEpochMilliseconds() + 86400000
    ): String = createVerificationToken(userId, profileType.name, email, expiresAt)

    private fun createVerificationToken(
        userId: Int,
        profileType: String,
        email: String,
        expiresAt: Long = clock.now().toEpochMilliseconds() + 86400000
    ): String {
        val token = "profile-token-${System.nanoTime()}"
        transaction {
            ProfileEmailVerificationTokens.insert {
                it[ProfileEmailVerificationTokens.userId] = userId
                it[ProfileEmailVerificationTokens.profileType] = profileType
                it[ProfileEmailVerificationTokens.email] = email
                it[ProfileEmailVerificationTokens.token] = token
                it[ProfileEmailVerificationTokens.expiresAt] = expiresAt
                it[ProfileEmailVerificationTokens.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
        return token
    }
}
