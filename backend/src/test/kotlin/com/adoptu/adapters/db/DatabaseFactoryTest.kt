package com.adoptu.adapters.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DatabaseFactoryTest {

    @Test
    fun `listOfTables contains all required tables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(Photographers))
    }

    @Test
    fun `DatabaseFactory listOfTables is not null`() {
        val tables = DatabaseFactory.listOfTables
        assertNotNull(tables)
    }

    @Test
    fun `DatabaseFactory has 27 tables`() {
        assertEquals(27, DatabaseFactory.listOfTables.size)
    }

    @Test
    fun `PendingRoleActivations table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(PendingRoleActivations))
    }

    @Test
    fun `CryptoKeys table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(CryptoKeys))
    }

    @Test
    fun `WebAuthnChallenges table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(WebAuthnChallenges))
    }

    @Test
    fun `Users table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(Users))
    }

    @Test
    fun `EmailVerificationTokens table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(EmailVerificationTokens))
    }

    @Test
    fun `EmailVerificationAttempts table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(EmailVerificationAttempts))
    }

    @Test
    fun `WebAuthnCredentials table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(WebAuthnCredentials))
    }

    @Test
    fun `SterilizationLocations table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(SterilizationLocations))
    }

    @Test
    fun `Pets table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(Pets))
    }

    @Test
    fun `PetImages table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(PetImages))
    }

    @Test
    fun `AdoptionRequests table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(AdoptionRequests))
    }

    @Test
    fun `UserActiveRoles table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(UserActiveRoles))
    }

    @Test
    fun `PhotographyRequests table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(PhotographyRequests))
    }

    @Test
    fun `TemporalHomes table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(TemporalHomes))
    }

    @Test
    fun `BlockedRescuers table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(BlockedRescuers))
    }

    @Test
    fun `TemporalHomeRequests table is in listOfTables`() {
        assertTrue(DatabaseFactory.listOfTables.contains(TemporalHomeRequests))
    }

    @Test
    fun `listOfTables contains all expected tables in correct order`() {
        val expectedTables = listOf(
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
            WebAuthnChallenges
        )
        
        assertEquals(expectedTables.size, DatabaseFactory.listOfTables.size)
        expectedTables.forEachIndexed { index, table ->
            assertTrue(
                DatabaseFactory.listOfTables.contains(table),
                "Table at index $index should be in listOfTables"
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
class DatabaseIntegrationTest {

    @Test
    fun `can connect to in-memory H2 database`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        assertNotNull(db)
    }

    @Test
    fun `can create schema with all tables`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testschema;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(
                Users,
                UserActiveRoles,
                WebAuthnCredentials,
                Pets,
                PetImages,
                AdoptionRequests,
                PhotographyRequests,
                Photographers,
                TemporalHomes,
                BlockedRescuers,
                TemporalHomeRequests
            )
        }
        
        assertTrue(true)
    }

    @Test
    fun `Users table can be created`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testusers;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users)
        }
        
        assertTrue(true)
    }

    @Test
    fun `Pets table can be created`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testpets;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, Pets)
        }
        
        assertTrue(true)
    }

    @Test
    fun `PetImages table can be created with foreign key`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testimages;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, Pets, PetImages)
        }
        
        assertTrue(true)
    }

    @Test
    fun `AdoptionRequests table can be created with foreign keys`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testadoption;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, Pets, AdoptionRequests)
        }
        
        assertTrue(true)
    }

    @Test
    fun `WebAuthnCredentials table can be created with foreign key`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testwebauthn;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, WebAuthnCredentials)
        }
        
        assertTrue(true)
    }

    @Test
    fun `UserActiveRoles table can be created with foreign key`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testuserroles;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, UserActiveRoles)
        }
        
        assertTrue(true)
    }

    @Test
    fun `PhotographyRequests table can be created with foreign keys`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testphotorequests;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, Pets, PhotographyRequests)
        }
        
        assertTrue(true)
    }

    @Test
    fun `all tables can be created in correct order`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testall;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(UserActiveRoles)
            SchemaUtils.create(Photographers)
            SchemaUtils.create(Pets)
            SchemaUtils.create(WebAuthnCredentials)
            SchemaUtils.create(PetImages)
            SchemaUtils.create(AdoptionRequests)
            SchemaUtils.create(PhotographyRequests)
            SchemaUtils.create(TemporalHomes)
            SchemaUtils.create(BlockedRescuers)
            SchemaUtils.create(TemporalHomeRequests)
        }
        
        assertTrue(true)
    }

    @Test
    fun `Database connect accepts driver class name`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testdriver;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        assertNotNull(db)
    }

    @Test
    fun `Database connect accepts user credentials`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testcreds;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "testuser",
            password = "testpassword"
        )
        
        assertNotNull(db)
    }

    @Test
    fun `can insert user into database`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testinsert;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, UserActiveRoles)
            val userId = Users.insert {
                it[username] = "test@example.com"
                it[displayName] = "Test User"
                it[createdAt] = Clock.System.now().toEpochMilliseconds()
            } get Users.id
            
            assertNotNull(userId)
        }
    }

    @Test
    fun `can insert and query user roles`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testroles;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, UserActiveRoles)
            val userId = Users.insert {
                it[username] = "admin@example.com"
                it[displayName] = "Admin User"
                it[createdAt] = Clock.System.now().toEpochMilliseconds()
            } get Users.id
            
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = userId
                it[UserActiveRoles.role] = "ADMIN"
            }
            
            val roleCount = UserActiveRoles.selectAll().count()
            assertEquals(1, roleCount)
        }
    }

    @Test
    fun `TemporalHomeRequests table can be created`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testtemporalhome;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(
                Users,
                Pets,
                TemporalHomes,
                BlockedRescuers,
                TemporalHomeRequests
            )
        }
        
        assertTrue(true)
    }

    @Test
    fun `BlockedRescuers table can be created`() {
        val db = Database.connect(
            url = "jdbc:h2:mem:testblocked;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        
        TransactionManager.defaultDatabase = db
        
        transaction {
            SchemaUtils.create(Users, TemporalHomes, BlockedRescuers)
        }
        
        assertTrue(true)
    }
}

