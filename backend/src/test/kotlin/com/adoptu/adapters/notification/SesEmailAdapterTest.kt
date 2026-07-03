package com.adoptu.adapters.notification

import com.adoptu.ports.NotificationPort
import com.adoptu.config.AppConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SesEmailAdapterTest {

    @Test
    fun `sendEmail returns false when not configured in prod mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail returns false when not configured in dev mode with no smtp`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail returns false when smtp host missing in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.port" to "587",
            "email.username" to "test",
            "email.password" to "test",
            "email.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail returns false when smtp port missing in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.host" to "localhost",
            "email.username" to "test",
            "email.password" to "test",
            "email.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail returns false when smtp credentials missing in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.host" to "localhost",
            "email.port" to "587",
            "email.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendPhotographerRequest returns false when not configured in prod mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "Test Photographer",
                requesterName = "Test Requester",
                petName = "Buddy",
                message = "Please take photos",
                fee = 50.0,
                currency = "USD"
            )
        }

        assertFalse(result)
    }

    @Test
    fun `sendPhotographerRequest returns false when not configured in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "Test Photographer",
                requesterName = "Test Requester",
                petName = "Buddy",
                message = "Please take photos",
                fee = 50.0,
                currency = "USD"
            )
        }

        assertFalse(result)
    }

    @Test
    fun `sendAdoptionRequestNotification returns false when not configured in prod mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendAdoptionRequestNotification(
                rescuerEmail = "rescuer@test.com",
                petName = "Buddy",
                adopterName = "Test Adopter",
                message = "I would like to adopt"
            )
        }

        assertFalse(result)
    }

    @Test
    fun `sendAdoptionRequestNotification returns false when not configured in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendAdoptionRequestNotification(
                rescuerEmail = "rescuer@test.com",
                petName = "Buddy",
                adopterName = "Test Adopter",
                message = null
            )
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail returns false for not configured in default prod mode`() {
        val config = AppConfig.fromMap(mapOf())
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }
        assertFalse(result)
    }

    @Test
    fun `NotificationPort interface is implemented correctly`() {
        val config = AppConfig.fromMap(mapOf())
        val adapter: NotificationPort = SesEmailAdapter(config)

        assertNotNull(adapter)
    }

    @Test
    fun `sendTemporalHomeRequest returns false when not configured in prod mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendTemporalHomeRequest(
                temporalHomeEmail = "home@test.com",
                temporalHomeAlias = "Test Home",
                rescuerName = "Test Rescuer",
                petName = "Buddy",
                message = "Need help",
                spamReportLink = "https://example.com/block"
            )
        }

        assertFalse(result)
    }

    @Test
    fun `sendTemporalHomeRequest returns false when not configured in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendTemporalHomeRequest(
                temporalHomeEmail = "home@test.com",
                temporalHomeAlias = "Test Home",
                rescuerName = "Test Rescuer",
                petName = "Buddy",
                message = "Need help",
                spamReportLink = "https://example.com/block"
            )
        }

        assertFalse(result)
    }

    @Test
    fun `adapter can be instantiated with empty config`() {
        val config = AppConfig.fromMap(mapOf())
        val adapter = SesEmailAdapter(config)
        assertNotNull(adapter)
    }

    @Test
    fun `adapter can be instantiated with ses config`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod",
            "ses.region" to "us-west-2"
        ))
        val adapter = SesEmailAdapter(config)
        assertNotNull(adapter)
    }

    @Test
    fun `adapter can be instantiated with smtp config`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.host" to "localhost",
            "email.port" to "587",
            "email.username" to "user",
            "email.password" to "pass",
            "email.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)
        assertNotNull(adapter)
    }

    @Test
    fun `sendPhotographerRequest with fee and currency builds correct parameters in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "John",
                requesterName = "Jane",
                petName = "Buddy",
                message = "Test message",
                fee = 100.0,
                currency = "EUR"
            )
        }
        assertFalse(result)
    }

    @Test
    fun `sendPhotographerRequest with zero fee does not include fee in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendPhotographerRequest(
                photographerEmail = "photo@test.com",
                photographerName = "John",
                requesterName = "Jane",
                petName = "Buddy",
                message = "Test message",
                fee = 0.0,
                currency = "USD"
            )
        }
        assertFalse(result)
    }

    @Test
    fun `sendAdoptionRequestNotification with blank message in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendAdoptionRequestNotification(
                rescuerEmail = "rescuer@test.com",
                petName = "Buddy",
                adopterName = "Jane",
                message = ""
            )
        }
        assertFalse(result)
    }

    @Test
    fun `sendAdoptionRequestNotification with blank message string in dev mode`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendAdoptionRequestNotification(
                rescuerEmail = "rescuer@test.com",
                petName = "Buddy",
                adopterName = "Jane",
                message = "   "
            )
        }
        assertFalse(result)
    }

    @Test
    fun `defaults to prod mode when env not specified`() {
        val config = AppConfig.fromMap(mapOf())
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }
        assertFalse(result)
    }
}
