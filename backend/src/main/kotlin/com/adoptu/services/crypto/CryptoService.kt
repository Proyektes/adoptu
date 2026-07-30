package com.adoptu.services.crypto

import com.adoptu.adapters.db.CryptoKeys
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object CryptoService {
    private const val ALGORITHM = "RSA/ECB/OAEPPadding"
    private const val KEY_ALGORITHM = "RSA"
    private const val KEY_SIZE = 2048

    // Every instance of the keypair lives in exactly one row of CryptoKeys (see Models.kt);
    // there is only ever one active RSA keypair for the whole deployment.
    private const val SINGLETON_ROW_ID = 1

    private var keyPair: KeyPair? = null

    private fun getOaepParameterSpec(): OAEPParameterSpec {
        return OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
    }

    // Synchronized so two concurrent first-callers on this JVM can't each generate and race to
    // persist their own keypair - only one persisted row must ever win, and every ECS task must
    // converge on that SAME row instead of each task using whichever keypair it generated itself.
    @Synchronized
    fun initialize() {
        if (keyPair == null) {
            keyPair = loadOrCreatePersistedKeyPair()
        }
    }

    private fun loadOrCreatePersistedKeyPair(): KeyPair = transaction {
        val existing = CryptoKeys.selectAll().where { CryptoKeys.id eq SINGLETON_ROW_ID }.firstOrNull()
        if (existing != null) {
            return@transaction decodeKeyPair(existing[CryptoKeys.publicKey], existing[CryptoKeys.privateKey])
        }

        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(KEY_SIZE, SecureRandom())
        val generated = generator.generateKeyPair()

        try {
            CryptoKeys.insert {
                it[id] = SINGLETON_ROW_ID
                it[publicKey] = Base64.getEncoder().encodeToString(generated.public.encoded)
                it[privateKey] = Base64.getEncoder().encodeToString(generated.private.encoded)
                it[createdAt] = System.currentTimeMillis()
            }
            generated
        } catch (e: Exception) {
            // Another ECS task/thread won the race to persist the first keypair - use theirs so
            // every instance converges on one key instead of each keeping its own.
            val row = CryptoKeys.selectAll().where { CryptoKeys.id eq SINGLETON_ROW_ID }.firstOrNull()
                ?: throw e
            decodeKeyPair(row[CryptoKeys.publicKey], row[CryptoKeys.privateKey])
        }
    }

    private fun decodeKeyPair(publicKeyBase64: String, privateKeyBase64: String): KeyPair {
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)))
        return KeyPair(publicKey, privateKey)
    }

    // Test-only: drops the cached keypair so the next initialize() call re-reads (or creates)
    // the persisted row, simulating a fresh ECS task/JVM picking up the shared keypair instead
    // of reusing this process's in-memory copy.
    internal fun resetForTesting() {
        keyPair = null
    }

    fun generateKeyPair(): Pair<String, String> {
        initialize()
        val publicKey = Base64.getEncoder().encodeToString(keyPair!!.public.encoded)
        val privateKey = Base64.getEncoder().encodeToString(keyPair!!.private.encoded)
        return publicKey to privateKey
    }

    fun getPublicKey(): String {
        initialize()
        return Base64.getEncoder().encodeToString(keyPair!!.public.encoded)
    }

    fun encrypt(plaintext: String, publicKeyBase64: String): String? {
        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec) as PublicKey

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, getOaepParameterSpec())

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.getUrlEncoder().encodeToString(ciphertext)
        } catch (e: Exception) {
            System.err.println("Encryption error: ${e::class.java.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun decrypt(ciphertext: String): String? {
        return try {
            initialize()
            val privateKey = keyPair!!.private

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, getOaepParameterSpec())

            val ciphertextBytes = Base64.getUrlDecoder().decode(ciphertext)
            val plaintext = cipher.doFinal(ciphertextBytes)
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            System.err.println("Decryption error: ${e::class.java.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun decryptWithKey(ciphertext: String, privateKeyBase64: String): String? {
        return try {
            val keyBytes = Base64.getDecoder().decode(privateKeyBase64)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec) as PrivateKey

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, getOaepParameterSpec())

            val ciphertextBytes = Base64.getUrlDecoder().decode(ciphertext)
            val plaintext = cipher.doFinal(ciphertextBytes)
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}