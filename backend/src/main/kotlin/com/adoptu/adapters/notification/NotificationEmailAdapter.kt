package com.adoptu.adapters.notification

import com.adoptu.ports.NotificationPort
import com.universaliun.email.common.EmailSenderPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NotificationEmailAdapter")

/**
 * [NotificationPort] implementation delegating actual dispatch to EmailKit's [EmailSenderPort]
 * (SMTP via Mailpit in dev, SES in prod -- see [com.adoptu.di.emailSenderPortFromConfig]) instead
 * of this project's own raw SMTP-socket/AWS-SES-SDK code, which is now EmailKit's problem to
 * maintain. Every hand-built template ([sendPhotographerRequest]/[sendAdoptionRequestNotification]/
 * [sendTemporalHomeRequest]) is unchanged -- only the send mechanism moved. Bodies here are plain
 * text, matching this adapter's previous behavior exactly (the old SES/SMTP send paths always sent
 * `text/plain`, never HTML), so every call passes `isHtml = false`.
 */
class NotificationEmailAdapter(
    private val emailSender: EmailSenderPort,
) : NotificationPort {

    override suspend fun sendEmail(to: String, subject: String, body: String, userId: Int?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                emailSender.send(to = listOf(to), subject = subject, body = body, isHtml = false)
                true
            } catch (e: Exception) {
                logger.error("Failed to send email to $to: ${e.message}")
                false
            }
        }

    override suspend fun sendPhotographerRequest(
        photographerEmail: String,
        photographerName: String,
        requesterName: String,
        petName: String?,
        message: String,
        fee: Double?,
        currency: String?
    ): Boolean {
        val subject = "New Photography Session Request - Adopt-U"
        val body = buildString {
            appendLine("Hello $photographerName,")
            appendLine()
            appendLine("You have received a new photography session request!")
            appendLine()
            appendLine("Request from: $requesterName")
            if (petName != null) {
                appendLine("Pet: $petName")
            }
            if (fee != null && fee > 0) {
                appendLine("Offered fee: $currency $fee")
            }
            appendLine()
            appendLine("Message:")
            appendLine(message)
            appendLine()
            appendLine("Log in to your account to respond to this request.")
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(photographerEmail, subject, body)
    }

    override suspend fun sendAdoptionRequestNotification(
        rescuerEmail: String,
        petName: String,
        adopterName: String,
        message: String?
    ): Boolean {
        val subject = "New Adoption Request for $petName"
        val body = buildString {
            appendLine("Hello,")
            appendLine()
            appendLine("You have received a new adoption request for $petName.")
            appendLine()
            appendLine("Adopter: $adopterName")
            if (!message.isNullOrBlank()) {
                appendLine()
                appendLine("Message:")
                appendLine(message)
            }
            appendLine()
            appendLine("Log in to your account to review the request.")
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(rescuerEmail, subject, body)
    }

    override suspend fun sendTemporalHomeRequest(
        temporalHomeEmail: String,
        temporalHomeAlias: String,
        rescuerName: String,
        petName: String?,
        message: String,
        spamReportLink: String
    ): Boolean {
        val subject = "New Pet Care Help Request - Adopt-U"
        val body = buildString {
            appendLine("Hello $temporalHomeAlias,")
            appendLine()
            appendLine("You have received a new pet care help request!")
            appendLine()
            appendLine("Request from: $rescuerName")
            if (petName != null) {
                appendLine("Pet: $petName")
            }
            appendLine()
            appendLine("Message:")
            appendLine(message)
            appendLine()
            appendLine("---")
            appendLine("If you want to block this rescuer from sending you more requests, click here:")
            appendLine(spamReportLink)
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(temporalHomeEmail, subject, body)
    }

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
    ): Boolean {
        val subject = "New Sponsorship Offer - Adopt-U"
        val body = buildString {
            appendLine("Hello $rescuerName,")
            appendLine()
            appendLine("You have received a new sponsorship offer!")
            appendLine()
            appendLine("From: $sponsorName")
            if (petName != null) {
                appendLine("For: $petName")
            } else {
                appendLine("For: your general fund")
            }
            if (offerType == "MONEY" && amount != null) {
                appendLine("Offering: $amount ${currency ?: ""}")
            } else if (inKindDescription != null) {
                appendLine("Offering (in-kind): $inKindDescription")
            }
            appendLine()
            appendLine("Message:")
            appendLine(message)
            appendLine()
            appendLine("Reply directly to this email or reach out via the contact details they provided to arrange the details.")
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(rescuerEmail, subject, body)
    }

    override suspend fun sendUrgentRescueAlert(
        rescuerEmail: String,
        rescuerName: String,
        description: String,
        dangerType: String,
        locationLabel: String,
        acceptLink: String
    ): Boolean {
        val subject = "URGENT: A pet needs immediate help near $locationLabel - Adopt-U"
        val body = buildString {
            appendLine("Hello $rescuerName,")
            appendLine()
            appendLine("A pet in danger has been reported near your coverage area and needs immediate help.")
            appendLine()
            appendLine("Type: $dangerType")
            appendLine("Location: $locationLabel")
            appendLine()
            appendLine("Description:")
            appendLine(description)
            appendLine()
            appendLine("This alert went out to every urgent rescuer covering this area - the first to accept")
            appendLine("gets it. Tap the link below to accept now:")
            appendLine(acceptLink)
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(rescuerEmail, subject, body)
    }
}
