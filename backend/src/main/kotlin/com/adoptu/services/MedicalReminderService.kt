package com.adoptu.services

import com.adoptu.dto.input.MedicalEventCategory
import com.adoptu.dto.input.PetMedicalEventDto
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetMedicalEventRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("MedicalReminderService")
private const val DAY_MS = 24 * 60 * 60 * 1000L

// Scans PetMedicalEvents for due/overdue vaccination or deworming records and emails the owning
// rescuer. Run by MedicalReminderScheduler's in-process daily loop (see Application.kt's main())
// rather than any external cron - see infra note there for why. Each of the 3 stages fires at
// most once per record (idempotency guards on PetMedicalEvents), and windows are wider than a
// single day so a missed scheduler run (deploy downtime, restart) still catches up next run
// instead of silently skipping a record's reminder.
@OptIn(ExperimentalTime::class)
class MedicalReminderService(
    private val medicalEventRepository: PetMedicalEventRepositoryPort,
    private val petRepository: PetRepositoryPort,
    private val userService: UserService,
    private val notificationPort: NotificationPort,
    private val clock: Clock,
    private val baseUrl: String
) {
    suspend fun sendDueReminders() {
        val now = clock.now().toEpochMilliseconds()
        val events = medicalEventRepository.getEventsWithPendingReminders()
        for (event in events) {
            try {
                processEvent(event, now)
            } catch (e: Exception) {
                logger.error("Failed to process reminder for medical event ${event.id}", e)
            }
        }
    }

    private suspend fun processEvent(event: PetMedicalEventDto, now: Long) {
        val dueDate = event.nextDueDate ?: return
        val daysUntilDue = Math.floorDiv(dueDate - now, DAY_MS)

        when {
            !event.reminder7dSent && daysUntilDue in 1..7 -> {
                sendReminder(event, ReminderStage.UPCOMING)
                medicalEventRepository.markReminder7dSent(event.id)
            }
            !event.reminderDueSent && daysUntilDue in -6..0 -> {
                sendReminder(event, ReminderStage.DUE)
                medicalEventRepository.markReminderDueSent(event.id)
            }
            !event.reminderOverdueSent && daysUntilDue <= -7 -> {
                sendReminder(event, ReminderStage.OVERDUE)
                medicalEventRepository.markReminderOverdueSent(event.id)
            }
        }
    }

    private suspend fun sendReminder(event: PetMedicalEventDto, stage: ReminderStage) {
        val pet = petRepository.getById(event.petId) ?: return
        val rescuer = userService.getById(pet.rescuerId) ?: return
        val categoryLabel = if (event.category == MedicalEventCategory.VACCINATION) "vaccination" else "deworming"
        val subject = when (stage) {
            ReminderStage.UPCOMING -> "Upcoming ${categoryLabel} due soon for ${pet.name} - Adopt-U"
            ReminderStage.DUE -> "${categoryLabel.replaceFirstChar(Char::uppercase)} due for ${pet.name} - Adopt-U"
            ReminderStage.OVERDUE -> "${categoryLabel.replaceFirstChar(Char::uppercase)} overdue for ${pet.name} - Adopt-U"
        }
        val statusText = when (stage) {
            ReminderStage.UPCOMING -> "is coming up soon"
            ReminderStage.DUE -> "is due"
            ReminderStage.OVERDUE -> "is overdue"
        }
        val body = "Hi ${rescuer.displayName},\n\n${pet.name}'s ${event.name} $categoryLabel $statusText. " +
            "$baseUrl/my-pets?edit=${pet.id}"
        notificationPort.sendEmail(to = rescuer.username, subject = subject, body = body)
    }

    private enum class ReminderStage { UPCOMING, DUE, OVERDUE }
}
