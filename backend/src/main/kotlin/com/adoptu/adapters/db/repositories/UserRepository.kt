package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.*
import com.adoptu.common.Country
import com.adoptu.dto.input.AcceptTermsRequest
import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.ports.EmailVerificationTokenInfo
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.adapters.db.dbDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserRepository(private val clock: Clock) : UserRepositoryPort {

    // Kept as a plain blocking helper (nested transaction): it is invoked from within other
    // transaction {} blocks below, which cannot suspend.
    private fun getActiveRolesForUser(userId: Int): Set<UserRole> {
        return transaction {
            UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq userId }
                .map { row ->
                    try {
                        UserRole.valueOf(row[UserActiveRoles.role])
                    } catch (e: Exception) {
                        null
                    }
                }
                .filterNotNull()
                .toSet()
        }
    }

    override suspend fun getById(userId: Int): UserDto? = withContext(dbDispatcher) {
        transaction {
            Users.selectAll()
                .where { Users.id eq userId }
                .firstOrNull()
                ?.let { user ->
                    val activeRoles = getActiveRolesForUser(userId)
                    val photographerSettings = if (activeRoles.contains(UserRole.PHOTOGRAPHER)) {
                        Photographers.selectAll()
                            .where { Photographers.userId eq userId }
                            .firstOrNull()
                    } else null
                    UserDto(
                        id = user[Users.id],
                        username = user[Users.username],
                        email = user[Users.username],
                        displayName = user[Users.displayName],
                        language = user[Users.language],
                        country = user[Users.country]?.displayName,
                        isEmailVerified = user[Users.isEmailVerified],
                        activeRoles = activeRoles,
                        lastAcceptedPrivacyPolicy = user[Users.lastAcceptedPrivacyPolicy],
                        lastAcceptedTermsAndConditions = user[Users.lastAcceptedTermsAndConditions],
                        isBanned = user[Users.isBanned],
                        banReason = user[Users.banReason],
                        photographerFee = photographerSettings?.get(Photographers.photographerFee)?.toDouble(),
                        photographerCurrency = photographerSettings?.get(Photographers.photographerCurrency),
                        photographerCountry = photographerSettings?.get(Photographers.country)?.displayName,
                        photographerState = photographerSettings?.get(Photographers.state)
                    )
                }
        }
    }

    override suspend fun getByEmail(email: String): UserDto? = withContext(dbDispatcher) {
        transaction {
            Users.selectAll()
                .where { Users.username eq email }
                .firstOrNull()
                ?.let { user ->
                    val activeRoles = getActiveRolesForUser(user[Users.id])
                    UserDto(
                        id = user[Users.id],
                        username = user[Users.username],
                        email = user[Users.username],
                        displayName = user[Users.displayName],
                        language = user[Users.language],
                        country = user[Users.country]?.displayName,
                        isEmailVerified = user[Users.isEmailVerified],
                        activeRoles = activeRoles,
                        lastAcceptedPrivacyPolicy = user[Users.lastAcceptedPrivacyPolicy],
                        lastAcceptedTermsAndConditions = user[Users.lastAcceptedTermsAndConditions],
                        isBanned = user[Users.isBanned],
                        banReason = user[Users.banReason]
                    )
                }
        }
    }

    override suspend fun getAllUsers(): List<UserDto> = withContext(dbDispatcher) {
        transaction {
            Users.selectAll()
                .map { user ->
                    val activeRoles = getActiveRolesForUser(user[Users.id])
                    UserDto(
                        id = user[Users.id],
                        username = user[Users.username],
                        email = user[Users.username],
                        displayName = user[Users.displayName],
                        language = user[Users.language],
                        country = user[Users.country]?.displayName,
                        isEmailVerified = user[Users.isEmailVerified],
                        activeRoles = activeRoles,
                        lastAcceptedPrivacyPolicy = user[Users.lastAcceptedPrivacyPolicy],
                        lastAcceptedTermsAndConditions = user[Users.lastAcceptedTermsAndConditions],
                        isBanned = user[Users.isBanned],
                        banReason = user[Users.banReason]
                    )
                }
        }
    }

    override suspend fun getPhotographers(country: String?, state: String?): List<PhotographerDto> = withContext(dbDispatcher) {
        transaction {
            val parsedFilterCountry: Country? = if (!country.isNullOrBlank()) {
                Country.fromDisplayName(country) ?: return@transaction emptyList()
            } else null

            val photographerUserIds = UserActiveRoles.selectAll()
                .where { UserActiveRoles.role eq UserRole.PHOTOGRAPHER.name }
                .map { row -> row[UserActiveRoles.userId] }
                .distinct()

            photographerUserIds.mapNotNull { userId ->
                val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                val photographer = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()

                if (user != null) {
                    val photographerCountry = photographer?.get(Photographers.country)
                    val photographerState = photographer?.get(Photographers.state)

                    val matchesCountry = parsedFilterCountry == null || photographerCountry == parsedFilterCountry
                    val matchesState = state.isNullOrBlank() || photographerState == state

                    if (matchesCountry && matchesState) {
                        PhotographerDto(
                            userId = userId,
                            displayName = user[Users.displayName],
                            username = user[Users.username],
                            photographerFee = photographer?.get(Photographers.photographerFee)?.toDouble(),
                            photographerCurrency = photographer?.get(Photographers.photographerCurrency),
                            country = photographerCountry?.displayName,
                            state = photographerState
                        )
                    } else null
                } else null
            }
        }
    }

    override suspend fun getRescuers(): List<UserDto> {
        // Raw id lookup stays inside the transaction; getById (now suspend) is called afterward
        // since it cannot be invoked from within the transaction {} lambda.
        val rescuerIds = withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.selectAll()
                    .where { UserActiveRoles.role eq UserRole.RESCUER.name }
                    .map { row -> row[UserActiveRoles.userId] }
                    .distinct()
            }
        }
        return rescuerIds.mapNotNull { userId -> getById(userId) }
    }

    override suspend fun banUser(userId: Int, reason: String?): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                val rowsUpdated = Users.update({ Users.id eq userId }) {
                    it[Users.isBanned] = true
                    it[Users.banReason] = reason
                }
                rowsUpdated > 0
            }
        }
    }

    override suspend fun unbanUser(userId: Int): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                val rowsUpdated = Users.update({ Users.id eq userId }) {
                    it[Users.isBanned] = false
                    it[Users.banReason] = null
                }
                rowsUpdated > 0
            }
        }
    }

    override suspend fun isBanned(userId: Int): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                Users.selectAll()
                    .where { Users.id eq userId }
                    .firstOrNull()
                    ?.get(Users.isBanned) ?: false
            }
        }
    }

    override suspend fun isRoleActive(userId: Int, role: UserRole): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq role.name) }
                    .count() > 0
            }
        }
    }

    override suspend fun activateRescuerProfile(userId: Int): UserDto? {
        val user = getById(userId) ?: return null

        withContext(dbDispatcher) {
            transaction {
                val existingRole = UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.RESCUER.name) }
                    .firstOrNull()
                if (existingRole == null) {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = UserRole.RESCUER.name
                    }
                }
            }
        }
        return getById(userId)
    }

    override suspend fun deactivateRescuerProfile(userId: Int): UserDto? {
        withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.deleteWhere {
                    (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.RESCUER.name)
                }

                Pets.update({ Pets.rescuerId eq userId }) {
                    it[Pets.isPromoted] = false
                }
            }
        }
        return getById(userId)
    }

    override suspend fun activateTemporalHomeProfile(userId: Int): UserDto? {
        val user = getById(userId) ?: return null

        withContext(dbDispatcher) {
            transaction {
                val existingRole = UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.TEMPORAL_HOME.name) }
                    .firstOrNull()
                if (existingRole == null) {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = UserRole.TEMPORAL_HOME.name
                    }
                }
            }
        }
        return getById(userId)
    }

    override suspend fun deactivateTemporalHomeProfile(userId: Int): UserDto? {
        withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.deleteWhere {
                    (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.TEMPORAL_HOME.name)
                }
            }
        }
        return getById(userId)
    }

    override suspend fun activateShelterProfile(userId: Int): UserDto? {
        val user = getById(userId) ?: return null

        withContext(dbDispatcher) {
            transaction {
                val existingRole = UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.SHELTER.name) }
                    .firstOrNull()
                if (existingRole == null) {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = UserRole.SHELTER.name
                    }
                }
            }
        }
        return getById(userId)
    }

    override suspend fun deactivateShelterProfile(userId: Int): UserDto? {
        withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.deleteWhere {
                    (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.SHELTER.name)
                }
            }
        }
        return getById(userId)
    }

    override suspend fun activateSterilizationProfile(userId: Int): UserDto? {
        val user = getById(userId) ?: return null

        withContext(dbDispatcher) {
            transaction {
                val existingRole = UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.STERILIZATION_SERVICE.name) }
                    .firstOrNull()
                if (existingRole == null) {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = UserRole.STERILIZATION_SERVICE.name
                    }
                }
            }
        }
        return getById(userId)
    }

    override suspend fun deactivateSterilizationProfile(userId: Int): UserDto? {
        withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.deleteWhere {
                    (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.STERILIZATION_SERVICE.name)
                }
            }
        }
        return getById(userId)
    }

    override suspend fun updateProfile(userId: Int, displayName: String, language: String?, country: String?): UserDto? {
        if (displayName.isBlank()) {
            throw IllegalArgumentException("Display name cannot be empty")
        }
        val parsedCountry = country?.let {
            Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
        }
        withContext(dbDispatcher) {
            transaction {
                Users.update({ Users.id eq userId }) {
                    it[Users.displayName] = displayName
                    if (language != null) {
                        it[Users.language] = language
                    }
                    if (parsedCountry != null) {
                        it[Users.country] = parsedCountry
                    }
                }
            }
        }
        return getById(userId)
    }

    override suspend fun updateLanguage(userId: Int, language: String): UserDto? {
        if (language.isBlank()) {
            throw IllegalArgumentException("Language cannot be empty")
        }
        withContext(dbDispatcher) {
            transaction {
                Users.update({ Users.id eq userId }) {
                    it[Users.language] = language
                }
            }
        }
        return getById(userId)
    }

    override suspend fun updatePhotographerSettings(userId: Int, request: PhotographerSettingsRequest): PhotographerDto? {
        if (request.photographerFee < 0) {
            throw IllegalArgumentException("Photographer fee must be zero or positive")
        }

        val userExists = withContext(dbDispatcher) {
            transaction {
                Users.selectAll().where { Users.id eq userId }.firstOrNull() != null
            }
        }
        if (!userExists) return null

        val parsedCountry = request.country?.let {
            Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
        }

        withContext(dbDispatcher) {
            transaction {
                val existing = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()
                if (existing != null) {
                    Photographers.update({ Photographers.userId eq userId }) {
                        it[photographerFee] = BigDecimal.valueOf(request.photographerFee)
                        it[photographerCurrency] = request.photographerCurrency
                        it[country] = parsedCountry
                        it[state] = request.state
                    }
                } else {
                    Photographers.insert {
                        it[Photographers.userId] = userId
                        it[photographerFee] = BigDecimal.valueOf(request.photographerFee)
                        it[photographerCurrency] = request.photographerCurrency
                        it[country] = parsedCountry
                        it[state] = request.state
                    }
                }
            }
        }

        return withContext(dbDispatcher) {
            transaction {
                val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                val photographer = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()
                if (user != null) {
                    PhotographerDto(
                        userId = userId,
                        displayName = user[Users.displayName],
                        photographerFee = photographer?.get(Photographers.photographerFee)?.toDouble(),
                        photographerCurrency = photographer?.get(Photographers.photographerCurrency),
                        country = photographer?.get(Photographers.country)?.displayName,
                        state = photographer?.get(Photographers.state)
                    )
                } else null
            }
        }
    }

    override suspend fun acceptTerms(userId: Int, request: AcceptTermsRequest): UserDto? {
        val now = clock.now().toEpochMilliseconds()
        withContext(dbDispatcher) {
            transaction {
                Users.update({ Users.id eq userId }) {
                    if (request.acceptPrivacyPolicy) {
                        it[Users.lastAcceptedPrivacyPolicy] = now
                    }
                    if (request.acceptTermsAndConditions) {
                        it[Users.lastAcceptedTermsAndConditions] = now
                    }
                }
            }
        }
        return getById(userId)
    }

    override suspend fun isEmailVerified(userId: Int): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                Users.selectAll()
                    .where { Users.id eq userId }
                    .firstOrNull()
                    ?.get(Users.isEmailVerified) ?: false
            }
        }
    }

    override suspend fun setEmailVerified(userId: Int, verified: Boolean): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                val rowsUpdated = Users.update({ Users.id eq userId }) {
                    it[Users.isEmailVerified] = verified
                }
                rowsUpdated > 0
            }
        }
    }

    override suspend fun createEmailVerificationToken(userId: Int, token: String, expiresAt: Long): Boolean {
        return withContext(dbDispatcher) {
            transaction {
                try {
                    EmailVerificationTokens.insert {
                        it[EmailVerificationTokens.userId] = userId
                        it[EmailVerificationTokens.token] = token
                        it[EmailVerificationTokens.expiresAt] = expiresAt
                        it[EmailVerificationTokens.createdAt] = clock.now().toEpochMilliseconds()
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    override suspend fun verifyToken(token: String): Int? {
        return withContext(dbDispatcher) {
            transaction {
                val now = clock.now().toEpochMilliseconds()
                val tokenRow = EmailVerificationTokens
                    .selectAll()
                    .where { EmailVerificationTokens.token eq token }
                    .firstOrNull()

                if (tokenRow != null && tokenRow[EmailVerificationTokens.expiresAt] > now) {
                    tokenRow[EmailVerificationTokens.userId]
                } else {
                    null
                }
            }
        }
    }

    override suspend fun getUserIdByToken(token: String): Int? {
        return withContext(dbDispatcher) {
            transaction {
                EmailVerificationTokens
                    .selectAll()
                    .where { EmailVerificationTokens.token eq token }
                    .firstOrNull()
                    ?.get(EmailVerificationTokens.userId)
            }
        }
    }

    override suspend fun deleteVerificationTokens(userId: Int) {
        withContext(dbDispatcher) {
            transaction {
                EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId }
            }
        }
    }

    override suspend fun getVerificationAttemptsToday(userId: Int): Int {
        return withContext(dbDispatcher) {
            transaction {
                val startOfDay = getStartOfDayMillis()
                EmailVerificationAttempts.selectAll()
                    .where { (EmailVerificationAttempts.userId eq userId) and (EmailVerificationAttempts.createdAt greaterEq startOfDay) }
                    .count()
                    .toInt()
            }
        }
    }

    override suspend fun recordVerificationAttempt(userId: Int) {
        withContext(dbDispatcher) {
            transaction {
                EmailVerificationAttempts.insert {
                    it[EmailVerificationAttempts.userId] = userId
                    it[EmailVerificationAttempts.createdAt] = clock.now().toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun getLatestVerificationToken(userId: Int): EmailVerificationTokenInfo? {
        val result = withContext(dbDispatcher) {
            transaction {
                EmailVerificationTokens
                    .selectAll()
                    .where { EmailVerificationTokens.userId eq userId }
                    .orderBy(EmailVerificationTokens.createdAt, org.jetbrains.exposed.v1.core.SortOrder.DESC)
                    .firstOrNull()
            }
        }
        return result?.let {
            EmailVerificationTokenInfo(
                token = it[EmailVerificationTokens.token],
                expiresAt = it[EmailVerificationTokens.expiresAt],
                createdAt = it[EmailVerificationTokens.createdAt]
            )
        }
    }

    private fun getStartOfDayMillis(): Long {
        val now = clock.now().toEpochMilliseconds()
        val dayMillis = 24 * 60 * 60 * 1000L
        return now - (now % dayMillis)
    }
}
