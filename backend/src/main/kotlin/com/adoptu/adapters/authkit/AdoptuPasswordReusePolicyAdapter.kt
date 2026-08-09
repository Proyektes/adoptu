package com.adoptu.adapters.authkit

import com.universaliun.auth.backend.application.auth.verifyPassword
import com.universaliun.auth.backend.domain.port.out.PasswordReusePolicyPort
import com.universaliun.auth.common.identity.AuthUserId
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.time.Instant

/**
 * Blocks reusing any password from the last 2 years, backed by [PasswordHistoryTable] -- same
 * window and semantics as Mazmobi's own `PasswordPolicyService` (the most mature prior art in this
 * codebase family). [checkReuse] runs before a new password is accepted (both the forgot-password
 * reset flow and first-password-set, see `PasswordReusePolicyPort`'s own doc comment for which
 * AuthKit services consult it); [recordChange] runs only after that check already passed.
 */
class AdoptuPasswordReusePolicyAdapter : PasswordReusePolicyPort {

    override fun checkReuse(userId: AuthUserId, newRawPassword: String, currentPasswordHash: String?) {
        if (currentPasswordHash != null && verifyPassword(newRawPassword, currentPasswordHash)) {
            throw PasswordReuseException("You cannot reuse your current password.")
        }

        val cutoff = Instant.now().minus(REUSE_BLOCK_WINDOW)
        val recentHashes = transaction {
            PasswordHistoryTable
                .selectAll()
                .where { (PasswordHistoryTable.userId eq userId.value) and (PasswordHistoryTable.createdAt greaterEq cutoff) }
                .orderBy(PasswordHistoryTable.createdAt, SortOrder.DESC)
                .map { it[PasswordHistoryTable.passwordHash] }
        }

        if (recentHashes.any { verifyPassword(newRawPassword, it) }) {
            throw PasswordReuseException("You cannot reuse a password from the last 2 years.")
        }
    }

    override fun recordChange(userId: AuthUserId, newPasswordHash: String) {
        val now = Instant.now()
        transaction {
            PasswordHistoryTable.insert { row ->
                row[PasswordHistoryTable.userId] = userId.value
                row[passwordHash] = newPasswordHash
                row[createdAt] = now
            }

            val cutoff = now.minus(REUSE_BLOCK_WINDOW)
            PasswordHistoryTable.deleteWhere {
                (PasswordHistoryTable.userId eq userId.value) and (PasswordHistoryTable.createdAt less cutoff)
            }
        }
    }

    companion object {
        private val REUSE_BLOCK_WINDOW: Duration = Duration.ofDays(730)
    }
}

/** Propagated verbatim from [PasswordReusePolicyPort.checkReuse] -- see AuthKit's own doc comment
 *  on that method for why a host-defined exception type is expected to flow through unmodified. */
class PasswordReuseException(message: String) : RuntimeException(message)
