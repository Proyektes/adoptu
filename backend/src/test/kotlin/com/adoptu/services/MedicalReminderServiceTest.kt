package com.adoptu.services
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetMedicalEventRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.MedicalEventCategory
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.NotificationPort
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Fails on the first [sendEmail] call and succeeds on every subsequent one, so tests can verify
 * that [MedicalReminderService.sendDueReminders] keeps processing the remaining medical events
 * (and logs, rather than propagates) after one event's reminder throws.
 */
private class ThrowOnceNotificationAdapter : NotificationPort {
    private var callCount = 0
    val sentEmails = mutableListOf<String>()

    override suspend fun sendEmail(to: String, subject: String, body: String, userId: Int?): Boolean {
        callCount++
        if (callCount == 1) throw RuntimeException("boom")
        sentEmails.add(to)
        return true
    }

    override suspend fun sendPhotographerRequest(
        photographerEmail: String,
        photographerName: String,
        requesterName: String,
        petName: String?,
        message: String,
        fee: Double?,
        currency: String?
    ): Boolean = true

    override suspend fun sendAdoptionRequestNotification(
        rescuerEmail: String,
        petName: String,
        adopterName: String,
        message: String?
    ): Boolean = true

    override suspend fun sendTemporalHomeRequest(
        temporalHomeEmail: String,
        temporalHomeAlias: String,
        rescuerName: String,
        petName: String?,
        message: String,
        spamReportLink: String
    ): Boolean = true

    override suspend fun sendSponsorshipOffer(
        rescuerEmail: String,
        rescuerName: String,
        sponsorName: String,
        petName: String?,
        offerType: String,
        amount: Double?,
        currency: String?,
        inKindDescription: String?,
        message: String
    ): Boolean = true

    override suspend fun sendUrgentRescueAlert(
        rescuerEmail: String,
        rescuerName: String,
        description: String,
        dangerType: String,
        locationLabel: String,
        acceptLink: String
    ): Boolean = true
}

@OptIn(ExperimentalTime::class)
class MedicalReminderServiceTest {

    private lateinit var reminderService: MedicalReminderService
    private lateinit var medicalEventRepository: PetMedicalEventRepositoryImpl
    private lateinit var petRepository: PetRepositoryImpl
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private val dayMs = 24 * 60 * 60 * 1000L

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        transaction {
            try {
                Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "rescuer@mocks.com"
                    it[Users.displayName] = "Test Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { /* already exists */ }
            exec("DELETE FROM pet_medical_events")
            exec("DELETE FROM pets")
        }
        petRepository = PetRepositoryImpl(clock)
        medicalEventRepository = PetMedicalEventRepositoryImpl(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        val userRepository = UserRepository(clock)
        val photographerRepository = PhotographerRepositoryImpl(petRepository, userRepository, clock)
        val userService = UserService(userRepository, photographerRepository, AdoptuUserRepositoryAdapter())
        reminderService = MedicalReminderService(
            medicalEventRepository, petRepository, userService, mockNotificationAdapter, clock, "http://localhost:4000"
        )
    }

    private suspend fun createEventDueIn(days: Long): Int {
        val pet = petRepository.create(
            rescuerId = 1, name = "Buddy", type = "DOG", description = "Test",
            weight = 10.0, ageYears = 2, ageMonths = 0, sex = Gender.MALE, country = "United States"
        )
        val event = medicalEventRepository.create(
            pet.id,
            CreatePetMedicalEventRequest(
                category = MedicalEventCategory.VACCINATION,
                name = "Rabies",
                administeredDate = clock.now().toEpochMilliseconds(),
                nextDueDate = clock.now().toEpochMilliseconds() + days * dayMs
            )
        )
        return event.id
    }

    @Test
    fun `sends an upcoming reminder for a record due in 5 days`() = runBlocking {
        createEventDueIn(5)

        reminderService.sendDueReminders()

        assertEquals(1, mockNotificationAdapter.getSentEmails().size)
        assertTrue(mockNotificationAdapter.getSentEmails().first().subject.contains("Upcoming"))
    }

    @Test
    fun `sends a due reminder for a record due today`() = runBlocking {
        createEventDueIn(0)

        reminderService.sendDueReminders()

        assertEquals(1, mockNotificationAdapter.getSentEmails().size)
        assertTrue(mockNotificationAdapter.getSentEmails().first().subject.contains("due"))
    }

    @Test
    fun `sends an overdue reminder for a record 10 days overdue`() = runBlocking {
        createEventDueIn(-10)

        reminderService.sendDueReminders()

        assertEquals(1, mockNotificationAdapter.getSentEmails().size)
        assertTrue(mockNotificationAdapter.getSentEmails().first().subject.contains("overdue"))
    }

    @Test
    fun `sends nothing for a record due far in the future`() = runBlocking {
        createEventDueIn(20)

        reminderService.sendDueReminders()

        assertEquals(0, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `does not resend the same reminder stage on a second scan`() = runBlocking {
        createEventDueIn(5)

        reminderService.sendDueReminders()
        reminderService.sendDueReminders()

        assertEquals(1, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `sends a fresh due reminder after the upcoming one, as the date approaches`() = runBlocking {
        val eventId = createEventDueIn(5)

        reminderService.sendDueReminders() // sends "upcoming"
        clock.advanceMillis(5 * dayMs) // now due today
        reminderService.sendDueReminders() // sends "due"

        val emails = mockNotificationAdapter.getSentEmails()
        assertEquals(2, emails.size)
        assertTrue(emails[0].subject.contains("Upcoming"))
        assertTrue(emails[1].subject.contains("due"))

        val event = medicalEventRepository.getById(eventId)!!
        assertTrue(event.reminder7dSent)
        assertTrue(event.reminderDueSent)
    }

    @Test
    fun `keeps processing remaining events when one event's reminder throws`() = runBlocking {
        createEventDueIn(5)
        createEventDueIn(5)

        val userRepository = UserRepository(clock)
        val photographerRepository = PhotographerRepositoryImpl(petRepository, userRepository, clock)
        val userService = UserService(userRepository, photographerRepository, AdoptuUserRepositoryAdapter())

        val throwingAdapter = ThrowOnceNotificationAdapter()
        val serviceWithThrowingAdapter = MedicalReminderService(
            medicalEventRepository, petRepository, userService, throwingAdapter, clock, "http://localhost:4000"
        )

        serviceWithThrowingAdapter.sendDueReminders()

        assertEquals(1, throwingAdapter.sentEmails.size)
    }
}
