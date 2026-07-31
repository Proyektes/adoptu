package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.AuthKitRefreshTokens
import com.universaliun.auth.backend.domain.model.auth.RefreshToken
import com.universaliun.auth.backend.domain.port.out.RefreshTokenRepositoryPort
import com.universaliun.auth.common.identity.AuthUserId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/**
 * Bridges AuthKit's [RefreshTokenRepositoryPort] onto Postgres via `authkit_refresh_tokens` --
 * this app had no refresh-token concept at all before (cookie sessions were the whole story), so
 * AuthKit's own in-memory default would silently log every user out on every deploy/restart/task
 * recycle. Same "durable adapter instead of the library's in-memory default" reasoning as
 * [AdoptuPasskeyCeremonyStoreAdapter]/[AdoptuUserRepositoryAdapter].
 */
class AdoptuRefreshTokenRepositoryAdapter : RefreshTokenRepositoryPort {

    override fun save(token: RefreshToken): RefreshToken = transaction {
        val exists = AuthKitRefreshTokens.selectAll().where { AuthKitRefreshTokens.id eq token.id.toString() }.any()
        val userId = token.userId.value.toInt()

        if (exists) {
            AuthKitRefreshTokens.update({ AuthKitRefreshTokens.id eq token.id.toString() }) { row ->
                row[tokenHash] = token.tokenHash
                row[AuthKitRefreshTokens.userId] = userId
                row[expiresAt] = token.expiresAt.toEpochMilli()
                row[revoked] = token.revoked
                row[deviceInfo] = token.deviceInfo
            }
        } else {
            AuthKitRefreshTokens.insert { row ->
                row[id] = token.id.toString()
                row[tokenHash] = token.tokenHash
                row[AuthKitRefreshTokens.userId] = userId
                row[expiresAt] = token.expiresAt.toEpochMilli()
                row[revoked] = token.revoked
                row[deviceInfo] = token.deviceInfo
            }
        }
        token
    }

    override fun findByTokenHash(hash: String): RefreshToken? = transaction {
        AuthKitRefreshTokens.selectAll()
            .where { AuthKitRefreshTokens.tokenHash eq hash }
            .singleOrNull()
            ?.toRefreshToken()
    }

    override fun revokeAllForUser(userId: AuthUserId) {
        val id = userId.value.toIntOrNull() ?: return
        transaction {
            AuthKitRefreshTokens.update({ AuthKitRefreshTokens.userId eq id }) { it[revoked] = true }
        }
    }

    override fun deleteExpired() {
        transaction {
            AuthKitRefreshTokens.deleteWhere { AuthKitRefreshTokens.expiresAt lessEq System.currentTimeMillis() }
        }
    }

    private fun ResultRow.toRefreshToken() = RefreshToken(
        id = UUID.fromString(this[AuthKitRefreshTokens.id]),
        tokenHash = this[AuthKitRefreshTokens.tokenHash],
        userId = AuthUserId(this[AuthKitRefreshTokens.userId].toString()),
        expiresAt = Instant.ofEpochMilli(this[AuthKitRefreshTokens.expiresAt]),
        revoked = this[AuthKitRefreshTokens.revoked],
        deviceInfo = this[AuthKitRefreshTokens.deviceInfo],
    )
}
