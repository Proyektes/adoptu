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
}
