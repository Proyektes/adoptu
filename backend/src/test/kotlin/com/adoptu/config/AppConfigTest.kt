package com.adoptu.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `AppConfig.fromMap()` (used throughout the rest of the test suite to build ad-hoc configs) is
 * covered elsewhere; this file exists solely to exercise `AppConfig.load()`, which reads the real
 * bundled application.conf (via ConfigFactory.load()) that's also present on the test classpath
 * at src/test/resources/application.conf.
 */
class AppConfigTest {

    @Test
    fun `load reads the bundled application dot conf`() {
        val config = AppConfig.load()

        // src/test/resources/application.conf overrides env to "test" on the test classpath
        // (main's application.conf defaults it to "dev").
        val env = config.propertyOrNull("env")
        assertNotNull(env)
        assertEquals("test", env.getString())
    }

    @Test
    fun `load returns null for a path that does not exist`() {
        val config = AppConfig.load()

        assertEquals(null, config.propertyOrNull("this.path.does.not.exist"))
    }
}
