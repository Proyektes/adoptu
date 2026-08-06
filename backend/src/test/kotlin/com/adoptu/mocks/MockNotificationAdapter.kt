package com.adoptu.mocks

import com.adoptu.ports.NotificationPort

class MockNotificationAdapter : NotificationPort {
    private val sentEmails = mutableListOf<EmailRecord>()
    private var shouldFail = false

    data class EmailRecord(
        val to: String,
        val subject: String,
        val body: String,
        val userId: Int? = null
    )

    fun setFailMode(fail: Boolean) {
        shouldFail = fail
    }

    override suspend fun sendEmail(to: String, subject: String, body: String, userId: Int?): Boolean {
        if (shouldFail) {
            return false
        }
        sentEmails.add(EmailRecord(to, subject, body, userId))
        return true
    }

    override suspend fun sendAdoptionRequestNotification(
        rescuerEmail: String,
        petName: String,
        adopterName: String,
        message: String?
    ): Boolean {
        val subject = "New Adoption Request for $petName"
        val body = """
            Hello,
            
            You have received a new adoption request for $petName.
            
            Adopter: $adopterName
            Message: $message
            
            Please login to review and respond to this request.
        """.trimIndent()
        
        return sendEmail(rescuerEmail, subject, body)
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
        val body = "Request from: $requesterName, Pet: $petName, Message: $message"
        return sendEmail(photographerEmail, subject, body)
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
        val body = "Request from: $rescuerName, Pet: $petName, Message: $message\nSpam Report Link: $spamReportLink"
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
        val body = "From: $sponsorName, Pet: $petName, Type: $offerType, Amount: $amount $currency, InKind: $inKindDescription, Message: $message"
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
        val body = "Type: $dangerType, Location: $locationLabel, Description: $description\nAccept: $acceptLink"
        return sendEmail(rescuerEmail, subject, body)
    }

    fun getSentEmails(): List<EmailRecord> = sentEmails.toList()

    fun clear() {
        sentEmails.clear()
        shouldFail = false
    }
}
