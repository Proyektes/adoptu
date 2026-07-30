package com.adoptu.services.crypto

import com.adoptu.mocks.TestDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class CryptoServiceTest {

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
    }

    @Test
    fun `generateKeyPair returns a usable public and private key pair`() {
        val (publicKey, privateKey) = CryptoService.generateKeyPair()

        assertNotNull(publicKey)
        assertNotNull(privateKey)
        assertNotEquals(publicKey, privateKey)
    }

    @Test
    fun `generateKeyPair output can encrypt and decryptWithKey can decrypt`() {
        val (publicKey, privateKey) = CryptoService.generateKeyPair()

        val ciphertext = CryptoService.encrypt("hello world", publicKey)
        assertNotNull(ciphertext)

        val plaintext = CryptoService.decryptWithKey(ciphertext, privateKey)
        assertEquals("hello world", plaintext)
    }

    @Test
    fun `decryptWithKey returns null for garbage ciphertext`() {
        val (_, privateKey) = CryptoService.generateKeyPair()

        val result = CryptoService.decryptWithKey("not-valid-base64-ciphertext!!!", privateKey)
        assertNull(result)
    }

    @Test
    fun `decryptWithKey returns null for garbage private key`() {
        CryptoService.initialize()
        val publicKey = CryptoService.getPublicKey()
        val ciphertext = CryptoService.encrypt("hello world", publicKey)
        assertNotNull(ciphertext)

        val result = CryptoService.decryptWithKey(ciphertext, "not-a-valid-private-key")
        assertNull(result)
    }

    @Test
    fun `encrypt returns null for an invalid public key`() {
        val result = CryptoService.encrypt("hello world", "not-a-valid-public-key")
        assertNull(result)
    }

    @Test
    fun `decrypt round-trips through the shared singleton key pair`() {
        CryptoService.initialize()
        val publicKey = CryptoService.getPublicKey()

        val ciphertext = CryptoService.encrypt("round trip me", publicKey)
        assertNotNull(ciphertext)

        val plaintext = CryptoService.decrypt(ciphertext)
        assertEquals("round trip me", plaintext)
    }

    @Test
    fun `decrypt returns null for garbage ciphertext`() {
        CryptoService.initialize()
        val result = CryptoService.decrypt("not-valid-base64-ciphertext!!!")
        assertNull(result)
    }

    @Test
    fun `initialize loads the same persisted keypair across simulated ECS task restarts`() {
        // Force a clean slate: no cached in-memory keypair and no persisted row, so the first
        // initialize() below must generate and persist a brand-new one.
        CryptoService.resetForTesting()
        CryptoService.initialize()
        val publicKeyFromFirstInstance = CryptoService.getPublicKey()
        val ciphertext = CryptoService.encrypt("cross-instance secret", publicKeyFromFirstInstance)
        assertNotNull(ciphertext)

        // Drop the in-memory cache again to simulate a *different* ECS task/JVM handling the
        // next request - it must load the SAME row from Postgres rather than minting its own,
        // which is exactly the bug that broke login when requests landed on different tasks.
        CryptoService.resetForTesting()
        CryptoService.initialize()
        val publicKeyFromSecondInstance = CryptoService.getPublicKey()

        assertEquals(publicKeyFromFirstInstance, publicKeyFromSecondInstance)
        assertEquals("cross-instance secret", CryptoService.decrypt(ciphertext))
    }
}
