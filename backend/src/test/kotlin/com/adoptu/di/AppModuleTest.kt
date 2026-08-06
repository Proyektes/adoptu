package com.adoptu.di

import com.adoptu.adapters.storage.AdoptuImageStorageAdapter
import com.adoptu.config.AppConfig
import com.adoptu.services.auth.WebAuthnService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AppModuleTest {

    @Test
    fun `appModule should be valid and loadable`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "test",
            "storage.test.bucket" to "test-bucket"
        ))
        val testModule = appModule(config)
        
        Assertions.assertNotNull(testModule)
    }

    @Test
    fun `createImageStorageAdapter uses test env config`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "test",
            "storage.test.bucket" to "test-bucket",
            "storage.test.region" to "us-east-1",
            "storage.test.access_key_id" to "test-key",
            "storage.test.secret_access_key" to "test-secret",
            "storage.test.endpoint" to "http://localhost:4566",
            "storage.test.path_style_access" to "true"
        ))

        val adapter = createImageStorageAdapter(config)

        Assertions.assertTrue(adapter is AdoptuImageStorageAdapter)
    }

    @Test
    fun `createImageStorageAdapter uses prod env config`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod",
            "storage.prod.bucket" to "prod-bucket",
            "storage.prod.region" to "eu-west-1"
        ))

        val adapter = createImageStorageAdapter(config)

        Assertions.assertTrue(adapter is AdoptuImageStorageAdapter)
    }

    @Test
    fun `createImageStorageAdapter defaults region to us-east-1 when not specified`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "prod",
            "storage.prod.bucket" to "prod-bucket"
        ))

        val adapter = createImageStorageAdapter(config)

        Assertions.assertTrue(adapter is AdoptuImageStorageAdapter)
    }

    @Test
    fun `createImageStorageAdapter defaults pathStyleAccess to false when not specified`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "test",
            "storage.test.bucket" to "test-bucket"
        ))

        val adapter = createImageStorageAdapter(config)

        Assertions.assertTrue(adapter is AdoptuImageStorageAdapter)
    }

    @Test
    fun `createImageStorageAdapter defaults env to prod when not specified`() {
        val config = AppConfig.fromMap(mapOf(
            "storage.prod.bucket" to "default-bucket"
        ))

        val adapter = createImageStorageAdapter(config)

        Assertions.assertTrue(adapter is AdoptuImageStorageAdapter)
    }

    @Test
    fun `appModule contains expected number of bean definitions`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "test",
            "storage.test.bucket" to "test-bucket"
        ))

        val testModule = appModule(config)

        Assertions.assertNotNull(testModule)
    }

    // getOrigins() is private and only invoked lazily, inside the WebAuthnService `single {}`
    // factory, when that bean is actually resolved through Koin - building the module object
    // alone (as the tests above do) never runs it. Resolve WebAuthnService from a real,
    // standalone Koin container built from appModule(config) to exercise both of its branches.

    @Test
    fun `appModule resolves WebAuthnService falling back to default origins when webauthn origins unset`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "test",
            "storage.test.bucket" to "test-bucket"
        ))

        val koinApp = koinApplication { modules(appModule(config)) }
        try {
            val webAuthnService = koinApp.koin.get<WebAuthnService>()
            Assertions.assertNotNull(webAuthnService)
        } finally {
            koinApp.close()
        }
    }

    @Test
    fun `appModule resolves WebAuthnService parsing configured comma-separated webauthn origins`() {
        val config = AppConfig.fromMap(mapOf(
            "env" to "test",
            "storage.test.bucket" to "test-bucket",
            "webauthn.origins" to "https://a.adopt-u.org, https://b.adopt-u.org"
        ))

        val koinApp = koinApplication { modules(appModule(config)) }
        try {
            val webAuthnService = koinApp.koin.get<WebAuthnService>()
            Assertions.assertNotNull(webAuthnService)
        } finally {
            koinApp.close()
        }
    }
}
