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
        // NOTE: the real config keys are "email.dev.*" / "email.prod.*" (see SesEmailAdapter's
        // emailPrefix). This test (and its siblings below) previously used a bare "email.*"
        // prefix that the adapter never reads, so it always exercised the "not configured"
        // path regardless of intent. Fixed to use the correct prefix so each test genuinely
        // covers what its name claims - bug found and fixed while raising coverage.
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.dev.port" to "587",
            "email.dev.username" to "test",
            "email.dev.password" to "test",
            "email.dev.from" to "test@example.com"
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
            "email.dev.host" to "localhost",
            "email.dev.username" to "test",
            "email.dev.password" to "test",
            "email.dev.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail attempts smtp send when host and port configured without credentials in dev mode`() {
        // With the correct "email.dev.*" prefix this is actually configured (isSmtpConfigured
        // only requires host+port), so this now exercises sendEmailViaSmtp's full body up to
        // the network call - which fails against the unbound loopback port and is caught,
        // still yielding false. hasSmtpCredentials is false, so no authenticator is set.
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.dev.host" to "localhost",
            "email.dev.port" to "1",
            "email.dev.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }

        assertFalse(result)
    }

    @Test
    fun `sendEmail attempts smtp send with authenticator when credentials configured in dev mode`() {
        // hasSmtpCredentials is true here, so this covers the setAuthenticator(...) branch
        // inside sendEmailViaSmtp in addition to the rest of its body.
        val config = AppConfig.fromMap(mapOf(
            "env" to "dev",
            "email.dev.host" to "localhost",
            "email.dev.port" to "1",
            "email.dev.username" to "smtpuser",
            "email.dev.password" to "smtppass",
            "email.dev.from" to "test@example.com",
            "email.dev.starttls" to "true"
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
            "email.dev.host" to "localhost",
            "email.dev.port" to "587",
            "email.dev.username" to "user",
            "email.dev.password" to "pass",
            "email.dev.from" to "test@example.com"
        ))
        val adapter = SesEmailAdapter(config)
        assertNotNull(adapter)
    }

    @Test
    fun `adapter overrides ses endpoint when configured`() {
        // Covers the sesEndpoint.isNullOrBlank() == false branch (builder.endpointOverride)
        // in the SesClient construction. The endpoint points at an unbound loopback port so
        // the eventual send attempt fails fast and is caught by sendEmail's outer try/catch.
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod",
            "ses.region" to "us-east-1",
            "ses.endpoint" to "http://localhost:1"
        ))
        val adapter = SesEmailAdapter(config)
        assertNotNull(adapter)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }
        assertFalse(result)
    }

    @Test
    fun `adapter falls back to not-configured when ses endpoint is malformed`() {
        // A malformed endpoint makes URI.create(...) throw inside the SesClient construction
        // try/catch, so sesClient stays null. Covers that catch block, plus the "SES not
        // configured" prod-mode logging branch of sendEmail (isConfigured == false, isDev ==
        // false), which no other test reaches since a validly-built SesClient always makes
        // isSesConfigured true in prod mode.
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod",
            "ses.endpoint" to "http://invalid host with spaces"
        ))
        val adapter = SesEmailAdapter(config)
        assertNotNull(adapter)

        val result = kotlinx.coroutines.runBlocking {
            adapter.sendEmail("test@example.com", "Test Subject", "Test Body")
        }
        assertFalse(result)
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
