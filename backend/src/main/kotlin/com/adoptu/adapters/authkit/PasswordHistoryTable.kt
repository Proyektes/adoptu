package com.adoptu.adapters.authkit

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * Backs [AdoptuPasswordReusePolicyAdapter] -- AuthKit itself has no concept of password history
 * (see `PasswordReusePolicyPort`'s own doc comment), so this table is entirely adopt-u's own.
 * [userId] is a plain varchar matching [AdoptuUserRepositoryAdapter]'s own `AuthUserId` encoding
 * (`Users.id.toString()` -- an integer id stringified, not assumed to be a UUID).
 */
object PasswordHistoryTable : Table("password_history") {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 255).index()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
