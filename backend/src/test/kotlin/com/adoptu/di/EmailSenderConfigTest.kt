package com.adoptu.di

import com.adoptu.config.AppConfig
import com.universaliun.email.backend.adapter.out.email.SesEmailAdapter
import com.universaliun.email.backend.adapter.out.email.SmtpEmailAdapter
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailSenderConfigTest {

    @Test
    fun `dev env routes through SmtpEmailAdapter`() {
        val config = AppConfig.fromMap(mapOf("env" to "dev", "email.dev.host" to "localhost", "email.dev.port" to "1025"))

        val port = emailSenderPortFromConfig(config)

        assertTrue(port is SmtpEmailAdapter, "expected SmtpEmailAdapter, got ${port::class.simpleName}")
    }

    @Test
    fun `dev env falls back to default host and port when unset`() {
        val config = AppConfig.fromMap(mapOf("env" to "dev"))

        val port = emailSenderPortFromConfig(config)

        assertTrue(port is SmtpEmailAdapter)
    }

    @Test
    fun `non-dev env routes through SesEmailAdapter`() {
        val config = AppConfig.fromMap(mapOf("env" to "prod", "ses.region" to "us-east-1"))

        val port = emailSenderPortFromConfig(config)

        assertTrue(port is SesEmailAdapter, "expected SesEmailAdapter, got ${port::class.simpleName}")
    }

    @Test
    fun `missing env defaults to prod`() {
        val config = AppConfig.fromMap(emptyMap())

        val port = emailSenderPortFromConfig(config)

        assertTrue(port is SesEmailAdapter)
    }

    @Test
    fun `a malformed ses endpoint degrades to a no-op sender instead of crashing`() {
        val config = AppConfig.fromMap(mapOf("env" to "prod", "ses.endpoint" to "://not a valid uri"))

        val port = emailSenderPortFromConfig(config)

        assertFalse(port is SesEmailAdapter)
        assertFalse(port is SmtpEmailAdapter)
        // NoOpEmailSenderPort is file-private to EmailSenderConfig.kt -- assert on its observable
        // behavior (send() logs and doesn't throw) rather than its type.
        port.send(to = listOf("someone@example.com"), subject = "test", body = "test", cc = emptyList(), bcc = emptyList(), isHtml = false)
    }
}
