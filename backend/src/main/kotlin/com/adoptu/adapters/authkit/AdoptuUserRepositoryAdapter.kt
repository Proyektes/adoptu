package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserPasswords
import com.adoptu.adapters.db.Users
import com.universaliun.auth.backend.domain.model.user.AuthUser
import com.universaliun.auth.backend.domain.model.user.Email
import com.universaliun.auth.backend.domain.port.out.UserRepositoryPort
import com.universaliun.auth.backend.domain.port.out.UserSearchResult
import com.universaliun.auth.common.identity.AuthUserId
import com.universaliun.auth.common.rbac.AuthPrincipal
import com.universaliun.auth.common.rbac.PermissionSet
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/**
 * Bridges AuthKit's [UserRepositoryPort] onto this app's own, already-working `users`/
 * `user_passwords` tables — same "point the library's port at our existing storage" pattern as
 * [AdoptuPasskeyCeremonyStoreAdapter]. Direct Exposed queries rather than wrapping the native
 * (fully `suspend`) `com.adoptu.ports.UserRepositoryPort`/`UserRepository`: AuthKit's own port is
 * synchronous (Helidon route handlers run one virtual thread per request, so blocking here is
 * fine, same as every other adapter in this package), and the shape needed (a single `AuthUser`
 * read across two tables, admin search, reset-token lookup) doesn't overlap cleanly with that
 * port's own `UserDto`-shaped, role/photographer-aware methods.
 *
 * ## Fields AuthKit doesn't own — never touched by [save]
 * `country`/`language`/`isBanned`/`banReason`/`lastAcceptedPrivacyPolicy`/
 * `lastAcceptedTermsAndConditions` and every photographer/pet table are Adopt-u's own and
 * completely untouched by this bridge — [save] only ever writes the columns AuthKit's own
 * `RegisterService`/`ConfirmEmailService`/`ResetPasswordService`/passkey services actually set
 * (`displayName`/`isEmailVerified`/`resetTokenHash`/`resetTokenExpiresAt`, plus `UserPasswords`
 * when a password hash is present). `user_active_roles` is likewise Adopt-u's own table (managed
 * entirely through `UserRepository`'s native `addActiveRoles`/`addPendingRoleActivations`, never
 * through this bridge's [save]) — [toAuthUser] only *reads* it, to populate [AuthUser.roles]/
 * [AuthUser.permissions] for the JWT AuthKit issues (see [AdoptuRole]/[AdoptuResource]). Since
 * AuthKit 1.3.0, authorization decisions never key off role identity — [toAuthUser] also derives
 * [AuthUser.allowedActions] from [activeRolesFor] here, granting
 * [AuthPrincipal.SUPER_ADMIN_ACTION] whenever any active role's [AdoptuRole.grantsAll] is `true`
 * (currently only [AdoptuRole.ADMIN]), so [AuthPrincipal.isSuperAdmin] keeps working for every user
 * who already holds the ADMIN role, with no separate persisted grant or data migration needed.
 *
 * ## `enabled` is derived, not stored
 * This app has no single `enabled` column — the real login gate is
 * `isEmailVerified && !isBanned && deactivatedAt IS NULL` (three separate native concepts this
 * app already enforces natively, none of which AuthKit ever touches directly except
 * `isEmailVerified` via [save]/[deactivate]/[reactivate]). [toAuthUser] computes it on read;
 * [save] only ever persists `isEmailVerified` from whatever `AuthUser.enabled`/`emailVerified`
 * combination the caller passed (see the register/confirm-email/passkey-signup call sites — all
 * of them set `enabled`/`emailVerified` to the same value together, so reading `emailVerified`
 * alone is lossless for those flows).
 */
class AdoptuUserRepositoryAdapter : UserRepositoryPort {

    override fun findById(id: AuthUserId): AuthUser? = transaction {
        val userId = id.value.toIntOrNull() ?: return@transaction null
        userRow(Users.id eq userId)?.toAuthUser()
    }

    override fun findByEmail(email: Email): AuthUser? = transaction {
        userRow(Users.username eq email.value)?.toAuthUser()
    }

    override fun existsByEmail(email: Email): Boolean = transaction {
        Users.selectAll().where { Users.username eq email.value }.any()
    }

    override fun findByResetTokenHash(hash: String): AuthUser? = transaction {
        userRow(Users.resetTokenHash eq hash)
            ?.toAuthUser()
            ?.takeIf { it.resetTokenExpiresAt?.isAfter(Instant.now()) ?: true }
    }

    override fun updateResetToken(id: AuthUserId, hash: String?, expiresAt: Instant?) {
        val userId = id.value.toIntOrNull() ?: return
        transaction {
            Users.update({ Users.id eq userId }) { row ->
                row[resetTokenHash] = hash
                row[resetTokenExpiresAt] = expiresAt?.toEpochMilli()
            }
        }
    }

    override fun save(user: AuthUser): AuthUser = transaction {
        val requestedId = user.id.value.toIntOrNull()
        val existingId = requestedId?.takeIf { candidateId -> Users.selectAll().where { Users.id eq candidateId }.any() }

        val savedId = if (existingId != null) {
            Users.update({ Users.id eq existingId }) { row ->
                row[displayName] = user.displayName
                row[isEmailVerified] = user.emailVerified
                row[resetTokenHash] = user.resetTokenHash
                row[resetTokenExpiresAt] = user.resetTokenExpiresAt?.toEpochMilli()
            }
            existingId
        } else {
            (Users.insert { row ->
                row[username] = user.email.value
                row[displayName] = user.displayName
                row[isEmailVerified] = user.emailVerified
                row[createdAt] = user.createdAt.toEpochMilli()
                row[resetTokenHash] = user.resetTokenHash
                row[resetTokenExpiresAt] = user.resetTokenExpiresAt?.toEpochMilli()
            } get Users.id)
        }

        val newPasswordHash = user.passwordHash
        if (newPasswordHash != null) {
            val hasPasswordRow = UserPasswords.selectAll().where { UserPasswords.userId eq savedId }.any()
            val now = System.currentTimeMillis()
            if (hasPasswordRow) {
                UserPasswords.update({ UserPasswords.userId eq savedId }) { row ->
                    row[passwordHash] = newPasswordHash
                    row[updatedAt] = now
                }
            } else {
                UserPasswords.insert { row ->
                    row[UserPasswords.userId] = savedId
                    row[passwordHash] = newPasswordHash
                    row[createdAt] = now
                    row[updatedAt] = now
                }
            }
        }

        user.copy(id = AuthUserId(savedId.toString()))
    }

    override fun search(query: String?, page: Int, pageSize: Int, includeInactive: Boolean): UserSearchResult = transaction {
        val condition: Op<Boolean> = buildList {
            if (!includeInactive) add(Users.deactivatedAt.isNull())
            if (!query.isNullOrBlank()) {
                val pattern = "%${query.trim().lowercase()}%"
                add((Users.displayName.lowerCase() like pattern) or (Users.username.lowerCase() like pattern))
            }
        }.fold(Op.TRUE as Op<Boolean>) { acc, op -> acc and op }

        val totalCount = Users.selectAll().where { condition }.count()
        val items = Users.selectAll()
            .where { condition }
            .limit(pageSize).offset(((page - 1).coerceAtLeast(0) * pageSize).toLong())
            .map { it.toAuthUser() }

        UserSearchResult(items, totalCount.toInt())
    }

    override fun deactivate(id: AuthUserId, by: AuthUserId) {
        val userId = id.value.toIntOrNull() ?: return
        val byId = by.value.toIntOrNull()
        transaction {
            Users.update({ Users.id eq userId }) { row ->
                row[deactivatedAt] = System.currentTimeMillis()
                row[deactivatedBy] = byId
            }
        }
    }

    override fun reactivate(id: AuthUserId) {
        val userId = id.value.toIntOrNull() ?: return
        transaction {
            Users.update({ Users.id eq userId }) { row ->
                row[deactivatedAt] = null
                row[deactivatedBy] = null
            }
        }
    }

    private fun userRow(condition: Op<Boolean>): ResultRow? =
        Users.selectAll().where { condition }.singleOrNull()

    // Nested query within the caller's own transaction { } block (same pattern as the
    // UserPasswords lookup below) - reads user_active_roles directly rather than going through
    // the native (suspend) UserRepository, same reasoning as this whole adapter's own doc comment.
    private fun activeRolesFor(userId: Int): Set<AdoptuRole> =
        UserActiveRoles.selectAll()
            .where { UserActiveRoles.userId eq userId }
            .mapNotNull { row -> runCatching { AdoptuRole.valueOf(row[UserActiveRoles.role]) }.getOrNull() }
            .toSet()

    private fun ResultRow.toAuthUser(): AuthUser {
        val userId = this[Users.id]
        val passwordHash = UserPasswords.select(UserPasswords.passwordHash)
            .where { UserPasswords.userId eq userId }
            .singleOrNull()
            ?.get(UserPasswords.passwordHash)

        val isEmailVerified = this[Users.isEmailVerified]
        val isBanned = this[Users.isBanned]
        val deactivatedAt = this[Users.deactivatedAt]
        val activeRoles = activeRolesFor(userId)

        return AuthUser(
            id = AuthUserId(userId.toString()),
            email = Email(this[Users.username]),
            displayName = this[Users.displayName],
            passwordHash = passwordHash,
            roles = activeRoles,
            permissions = PermissionSet.fromRoles(activeRoles, ADOPTU_RESOURCE_COUNT),
            allowedActions = if (activeRoles.any { it.grantsAll() }) setOf(AuthPrincipal.SUPER_ADMIN_ACTION) else emptySet(),
            enabled = isEmailVerified && !isBanned && deactivatedAt == null,
            emailVerified = isEmailVerified,
            createdAt = Instant.ofEpochMilli(this[Users.createdAt]),
            lastModifiedAt = Instant.now(),
            deactivatedAt = deactivatedAt?.let { Instant.ofEpochMilli(it) },
            deactivatedBy = this[Users.deactivatedBy]?.let { AuthUserId(it.toString()) },
            resetTokenHash = this[Users.resetTokenHash],
            resetTokenExpiresAt = this[Users.resetTokenExpiresAt]?.let { Instant.ofEpochMilli(it) },
        )
    }
}
