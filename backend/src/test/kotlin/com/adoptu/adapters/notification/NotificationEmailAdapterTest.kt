package com.adoptu.adapters.notification

import com.adoptu.ports.NotificationPort
import com.universaliun.email.common.EmailSenderPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingEmailSenderPort(private val throwOnSend: Boolean = false) : EmailSenderPort {
    data class Sent(val to: List<String>, val subject: String, val body: String, val isHtml: Boolean)

    val sent = mutableListOf<Sent>()

    override fun send(to: List<String>, subject: String, body: String, cc: List<String>, bcc: List<String>, isHtml: Boolean) {
        if (throwOnSend) throw RuntimeException("send failed")
        sent.add(Sent(to, subject, body, isHtml))
    }
}

class NotificationEmailAdapterTest {

    @Test
    fun `sendEmail delegates to EmailSenderPort as plain text and returns true on success`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        val result = runBlocking { adapter.sendEmail("user@test.com", "Subject", "Body") }

        assertTrue(result)
        assertEquals(1, sender.sent.size)
        assertEquals(listOf("user@test.com"), sender.sent.single().to)
        assertEquals("Subject", sender.sent.single().subject)
        assertFalse(sender.sent.single().isHtml)
    }

    @Test
    fun `sendEmail returns false and does not throw when the sender fails`() {
        val sender = RecordingEmailSenderPort(throwOnSend = true)
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        val result = runBlocking { adapter.sendEmail("user@test.com", "Subject", "Body") }

        assertFalse(result)
    }

    @Test
    fun `sendPhotographerRequest builds the expected subject and includes fee when positive`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        val result = runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "John",
                requesterName = "Jane",
                petName = "Buddy",
                message = "Please take photos",
                fee = 50.0,
                currency = "USD",
            )
        }

        assertTrue(result)
        val sent = sender.sent.single()
        assertEquals("New Photography Session Request - Adopt-U", sent.subject)
        assertTrue(sent.body.contains("Offered fee: USD 50.0"))
        assertTrue(sent.body.contains("Pet: Buddy"))
    }

    @Test
    fun `sendPhotographerRequest omits the fee line when fee is zero`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "John",
                requesterName = "Jane",
                petName = null,
                message = "Please take photos",
                fee = 0.0,
                currency = "USD",
            )
        }

        assertFalse(sender.sent.single().body.contains("Offered fee"))
    }

    @Test
    fun `sendAdoptionRequestNotification omits the message section when blank`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendAdoptionRequestNotification(
                rescuerEmail = "rescuer@test.com",
                petName = "Buddy",
                adopterName = "Jane",
                message = "   ",
            )
        }

        assertFalse(sender.sent.single().body.contains("Message:"))
    }

    @Test
    fun `sendTemporalHomeRequest includes the spam report link`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendTemporalHomeRequest(
                temporalHomeEmail = "home@test.com",
                temporalHomeAlias = "Casa",
                rescuerName = "Jane",
                petName = "Buddy",
                message = "Need help",
                spamReportLink = "https://example.com/block",
            )
        }

        assertTrue(sender.sent.single().body.contains("https://example.com/block"))
    }

    @Test
    fun `sendTemporalHomeRequest omits the pet line when petName is null`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendTemporalHomeRequest(
                temporalHomeEmail = "home@test.com",
                temporalHomeAlias = "Casa",
                rescuerName = "Jane",
                petName = null,
                message = "Need help",
                spamReportLink = "https://example.com/block",
            )
        }

        assertFalse(sender.sent.single().body.contains("Pet:"))
    }

    @Test
    fun `sendPhotographerRequest omits the fee line when fee is null`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "John",
                requesterName = "Jane",
                petName = "Buddy",
                message = "Please take photos",
                fee = null,
                currency = null,
            )
        }

        assertFalse(sender.sent.single().body.contains("Offered fee"))
    }

    @Test
    fun `sendAdoptionRequestNotification includes the message section when present`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendAdoptionRequestNotification(
                rescuerEmail = "rescuer@test.com",
                petName = "Buddy",
                adopterName = "Jane",
                message = "I would love to adopt Buddy!",
            )
        }

        val body = sender.sent.single().body
        assertTrue(body.contains("Message:"))
        assertTrue(body.contains("I would love to adopt Buddy!"))
    }

    @Test
    fun `sendSponsorshipOffer includes the offered amount when the offer type is money`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        val result = runBlocking {
            adapter.sendSponsorshipOffer(
                rescuerEmail = "rescuer@test.com",
                rescuerName = "Jane",
                sponsorName = "Sam",
                petName = "Buddy",
                offerType = "MONEY",
                amount = 100.0,
                currency = "USD",
                inKindDescription = null,
                message = "Happy to help!",
            )
        }

        assertTrue(result)
        val body = sender.sent.single().body
        assertEquals("New Sponsorship Offer - Adopt-U", sender.sent.single().subject)
        assertTrue(body.contains("For: Buddy"))
        assertTrue(body.contains("Offering: 100.0 USD"))
    }

    @Test
    fun `sendSponsorshipOffer describes the in-kind offer and general fund when petName is null`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendSponsorshipOffer(
                rescuerEmail = "rescuer@test.com",
                rescuerName = "Jane",
                sponsorName = "Sam",
                petName = null,
                offerType = "ITEM",
                amount = null,
                currency = null,
                inKindDescription = "Dog food and toys",
                message = "Happy to help!",
            )
        }

        val body = sender.sent.single().body
        assertTrue(body.contains("For: your general fund"))
        assertTrue(body.contains("Offering (in-kind): Dog food and toys"))
    }

    @Test
    fun `sendSponsorshipOffer omits the offering line when there is no amount or in-kind description`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        runBlocking {
            adapter.sendSponsorshipOffer(
                rescuerEmail = "rescuer@test.com",
                rescuerName = "Jane",
                sponsorName = "Sam",
                petName = "Buddy",
                offerType = "ITEM",
                amount = null,
                currency = null,
                inKindDescription = null,
                message = "Happy to help!",
            )
        }

        assertFalse(sender.sent.single().body.contains("Offering"))
    }

    @Test
    fun `sendUrgentRescueAlert delegates to sendEmail with the expected subject and body`() {
        val sender = RecordingEmailSenderPort()
        val adapter: NotificationPort = NotificationEmailAdapter(sender)

        val result = runBlocking {
            adapter.sendUrgentRescueAlert(
                rescuerEmail = "rescuer@test.com",
                rescuerName = "Jane",
                description = "Injured dog on the highway",
                dangerType = "TRAFFIC",
                locationLabel = "Downtown",
                acceptLink = "https://example.com/accept",
            )
        }

        assertTrue(result)
        val sent = sender.sent.single()
        assertEquals(listOf("rescuer@test.com"), sent.to)
        assertTrue(sent.subject.contains("URGENT"))
        assertTrue(sent.subject.contains("Downtown"))
        assertTrue(sent.body.contains("TRAFFIC"))
        assertTrue(sent.body.contains("Injured dog on the highway"))
        assertTrue(sent.body.contains("https://example.com/accept"))
    }
}
