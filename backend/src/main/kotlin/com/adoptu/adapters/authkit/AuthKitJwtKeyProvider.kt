package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.AuthKitJwtKeys
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Base64

/**
 * Provisions the RSA keypair AuthKit signs/verifies JWTs with, persisted so every ECS task
 * converges on the SAME key — same singleton-row-per-deployment pattern as
 * [com.adoptu.services.crypto.CryptoService], including the identical race-safe
 * "insert, and if another task won the race, read back what they persisted" fallback. This app
 * never issued JWTs before AuthKit (cookie sessions were the whole story), so there was no
 * existing keypair to bridge onto — this is new, not a migration of existing key material.
 */
object AuthKitJwtKeyProvider {
    private const val KEY_ALGORITHM = "RSA"
    private const val KEY_SIZE = 2048
    private const val SINGLETON_ROW_ID = 1

    /** @return (privateKeyBase64, publicKeyBase64) — the shape [authKoinModule] expects. */
    fun loadOrCreate(): Pair<String, String> = transaction {
        val existing = AuthKitJwtKeys.selectAll().where { AuthKitJwtKeys.id eq SINGLETON_ROW_ID }.firstOrNull()
        if (existing != null) {
            return@transaction existing[AuthKitJwtKeys.privateKey] to existing[AuthKitJwtKeys.publicKey]
        }

        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(KEY_SIZE, SecureRandom())
        val generated = generator.generateKeyPair()
        val privateKeyBase64 = Base64.getEncoder().encodeToString(generated.private.encoded)
        val publicKeyBase64 = Base64.getEncoder().encodeToString(generated.public.encoded)

        try {
            AuthKitJwtKeys.insert {
                it[id] = SINGLETON_ROW_ID
                it[privateKey] = privateKeyBase64
                it[publicKey] = publicKeyBase64
                it[createdAt] = System.currentTimeMillis()
            }
            privateKeyBase64 to publicKeyBase64
        } catch (e: Exception) {
            // Another ECS task/thread won the race to persist the first keypair -- use theirs.
            val row = AuthKitJwtKeys.selectAll().where { AuthKitJwtKeys.id eq SINGLETON_ROW_ID }.firstOrNull()
                ?: throw e
            row[AuthKitJwtKeys.privateKey] to row[AuthKitJwtKeys.publicKey]
        }
    }
}
