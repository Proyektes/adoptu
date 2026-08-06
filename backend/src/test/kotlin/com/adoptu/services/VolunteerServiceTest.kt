package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.VolunteerRepositoryImpl
import com.adoptu.dto.input.CreateVolunteerApplicationRequest
import com.adoptu.dto.input.VolunteerStatus
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class VolunteerServiceTest {

    private lateinit var service: VolunteerService
    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        transaction {
            fun user(id: Int, username: String) {
                try {
                    Users.insert {
                        it[Users.id] = id
                        it[Users.username] = username
                        it[Users.displayName] = username
                        it[Users.createdAt] = clock.now().toEpochMilliseconds()
                    }
                } catch (e: Exception) { /* already exists */ }
            }
            user(1, "rescuer1@mocks.com")
            user(2, "volunteer1@mocks.com")
            user(3, "plainuser@mocks.com")
            fun role(userId: Int, role: String) {
                try {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = role
                    }
                } catch (e: Exception) { /* already exists */ }
            }
            role(1, "RESCUER")
            exec("DELETE FROM volunteers")
        }
        val userRepository = UserRepository(clock)
        val photographerRepository = PhotographerRepositoryImpl(
            com.adoptu.adapters.db.repositories.PetRepositoryImpl(clock), userRepository, clock
        )
        val userService = UserService(userRepository, photographerRepository)
        val volunteerRepository = VolunteerRepositoryImpl(userRepository, clock)
        service = VolunteerService(volunteerRepository, userService)
    }

    @Test
    fun `apply succeeds for a real rescuer`() = runBlocking {
        val result = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))

        assertTrue(result is ServiceResult.Success)
        assertEquals(VolunteerStatus.PENDING, result.data.status)
        assertEquals(1, result.data.rescuerId)
        assertEquals(2, result.data.volunteerId)
    }

    @Test
    fun `apply returns Error when target is not an active rescuer`() = runBlocking {
        val result = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 3))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `apply returns Error when volunteering for yourself`() = runBlocking {
        val result = service.apply(1, CreateVolunteerApplicationRequest(rescuerId = 1))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `apply returns Error for a duplicate pending application`() = runBlocking {
        service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))

        val result = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `apply succeeds again after a rejection`() = runBlocking {
        val first = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))
        val id = (first as ServiceResult.Success).data.id
        service.updateStatus(id, VolunteerStatus.REJECTED, 1, setOf("RESCUER"))

        val result = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `updateStatus succeeds for the rescuer being volunteered for`() = runBlocking {
        val created = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))
        val id = (created as ServiceResult.Success).data.id

        val result = service.updateStatus(id, VolunteerStatus.ACTIVE, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(VolunteerStatus.ACTIVE, result.data.status)
    }

    @Test
    fun `updateStatus returns Forbidden for a non-target rescuer`() = runBlocking {
        val created = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))
        val id = (created as ServiceResult.Success).data.id

        val result = service.updateStatus(id, VolunteerStatus.ACTIVE, 3, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `updateStatus returns Error when trying to reset to PENDING`() = runBlocking {
        val created = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))
        val id = (created as ServiceResult.Success).data.id

        val result = service.updateStatus(id, VolunteerStatus.PENDING, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `isActiveVolunteerFor is false until approved, true after`() = runBlocking {
        val created = service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))
        val id = (created as ServiceResult.Success).data.id

        assertEquals(false, service.isActiveVolunteerFor(1, 2))

        service.updateStatus(id, VolunteerStatus.ACTIVE, 1, setOf("RESCUER"))

        assertEquals(true, service.isActiveVolunteerFor(1, 2))
    }

    @Test
    fun `getMyApplications returns applications for that volunteer`() = runBlocking {
        service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))

        val result = service.getMyApplications(2)

        assertEquals(1, result.size)
        assertEquals(1, result.first().rescuerId)
    }

    @Test
    fun `getApplicationsForRescuer returns Forbidden for a non-owner`() = runBlocking {
        val result = service.getApplicationsForRescuer(1, 3, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `getApplicationsForRescuer succeeds for the rescuer themselves`() = runBlocking {
        service.apply(2, CreateVolunteerApplicationRequest(rescuerId = 1))

        val result = service.getApplicationsForRescuer(1, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(1, result.data.size)
    }
}
