package com.adoptu.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * Drop-in replacement for Ktor's `io.ktor.server.config.ApplicationConfig` (same
 * `propertyOrNull(path)?.getString()` / `property(path).getString()` call shape), backed
 * directly by the Typesafe Config library Ktor's own HOCON config was already wrapping.
 * Lets AppModule.kt / DatabaseFactory.kt / SesEmailAdapter.kt / AuthRoutes.kt keep their
 * config-reading logic unchanged across the Helidon migration - only the import changes.
 */
class AppConfig(private val raw: Config) {

    fun propertyOrNull(path: String): ConfigValue? =
        if (raw.hasPath(path)) ConfigValue(raw, path) else null

    fun property(path: String): ConfigValue = ConfigValue(raw, path)

    companion object {
        fun load(): AppConfig = AppConfig(ConfigFactory.load())

        /** For tests: builds a config from a flat map of dotted paths, e.g. "db.test.postgres.url" -> "...". */
        fun fromMap(values: Map<String, Any>): AppConfig = AppConfig(ConfigFactory.parseMap(values))
    }
}

class ConfigValue(private val raw: Config, private val path: String) {
    fun getString(): String = raw.getString(path)
    fun getList(): List<String> = raw.getStringList(path)
}
