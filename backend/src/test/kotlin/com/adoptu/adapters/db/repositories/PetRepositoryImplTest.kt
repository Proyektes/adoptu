package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.dto.input.Gender
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * Direct repository-level tests for PetRepositoryImpl paths that PetServiceTest doesn't reach.
 * (Note: named PetRepositoryImplTest.kt, not PetRepositoryIT.kt -- build.gradle.kts excludes
 * every "IT"-suffixed test class from the default `test` task, which is what the Kover coverage
 * gate runs against; the pre-existing PetRepositoryIT.kt/UserRepositoryIT.kt/etc. stubs in this
 * package are empty placeholders for a future Docker/Testcontainers suite and don't run as part
 * of that gate.)
 *
 * - PetRepositoryPort.getAll/createAdoptionRequest declare default parameter values, but
 *   PetService.getAll/createAdoptionRequest always forward every argument explicitly (their own
 *   defaults already resolved to concrete values before calling the repo), so the repo
 *   interface's synthetic "$default" dispatcher for those two methods is never reached through
 *   the service layer. Calling the repository directly with arguments omitted exercises it.
 * - PetRepositoryImpl.removeImage's "image not found" branch: PetService.removeImage checks
 *   getImages()/find{} itself and returns NotFound *before* ever calling repository.removeImage,
 *   so the repo's own `image == null -> false` branch is unreachable via the service and needs a
 *   direct call.
 */
@OptIn(ExperimentalTime::class)
class PetRepositoryImplTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var repository: PetRepositoryImpl

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        repository = PetRepositoryImpl(clock)
    }

    private fun createRescuer(id: Int = 1): Int {
        return transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.username] = "rescuer$id@test.com"
                it[Users.displayName] = "Rescuer $id"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = id
                it[UserActiveRoles.role] = "RESCUER"
            }
            id
        }
    }

    private fun createAdopter(id: Int = 2): Int {
        return transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.username] = "adopter$id@test.com"
                it[Users.displayName] = "Adopter $id"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            id
        }
    }

    @Test
    fun `getAll with only country given uses default type and showPromotedOnly`() = runBlocking {
        val rescuerId = createRescuer()
        repository.create(
            rescuerId = rescuerId,
            name = "Buddy",
            type = "DOG",
            description = "Test description",
            weight = 10.0,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "United States"
        )

        val result = repository.getAll(country = "United States")

        assertEquals(1, result.size)
        assertEquals("Buddy", result.first().name)
    }

    @Test
    fun `createAdoptionRequest with only required args uses default optional fields`() = runBlocking {
        val rescuerId = createRescuer()
        val adopterId = createAdopter()
        val pet = repository.create(
            rescuerId = rescuerId,
            name = "Buddy",
            type = "DOG",
            description = "Test description",
            weight = 10.0,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "United States"
        )

        val request = repository.createAdoptionRequest(pet.id, adopterId, "I'd love to adopt!")

        assertEquals("PENDING", request.status)
        assertNull(request.housingType)
        assertNull(request.hasYard)
        assertNull(request.hasOtherPets)
        assertNull(request.experienceLevel)
    }

    @Test
    fun `removeImage returns false when the image does not exist`() = runBlocking {
        val rescuerId = createRescuer()
        val pet = repository.create(
            rescuerId = rescuerId,
            name = "Buddy",
            type = "DOG",
            description = "Test description",
            weight = 10.0,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "United States"
        )

        val removed = repository.removeImage(pet.id, 9999)

        assertFalse(removed)
    }

    @Test
    fun `removeImage returns true and deletes the row when the image exists`() = runBlocking {
        val rescuerId = createRescuer()
        val pet = repository.create(
            rescuerId = rescuerId,
            name = "Buddy",
            type = "DOG",
            description = "Test description",
            weight = 10.0,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "United States"
        )
        val image = repository.addImage(pet.id, "https://example.com/a.jpg")

        val removed = repository.removeImage(pet.id, image.id)

        assertEquals(true, removed)
        assertEquals(0, repository.getImages(pet.id).size)
    }
}
