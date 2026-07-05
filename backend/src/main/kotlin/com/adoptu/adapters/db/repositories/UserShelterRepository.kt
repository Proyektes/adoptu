package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserShelters
import com.adoptu.common.Country
import com.adoptu.dto.input.CreateUserShelterRequest
import com.adoptu.dto.input.UpdateUserShelterRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.input.UserShelterDto
import com.adoptu.ports.UserShelterRepositoryPort
import com.adoptu.adapters.db.dbDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserShelterRepository(private val clock: Clock) : UserShelterRepositoryPort {

    private fun rowToDto(row: org.jetbrains.exposed.v1.core.ResultRow): UserShelterDto {
        return UserShelterDto(
            userId = row[UserShelters.userId],
            name = row[UserShelters.name],
            country = row[UserShelters.country].displayName,
            state = row[UserShelters.state],
            city = row[UserShelters.city],
            neighborhood = row[UserShelters.neighborhood],
            address = row[UserShelters.address],
            zip = row[UserShelters.zip],
            phone = row[UserShelters.phone],
            email = row[UserShelters.email],
            emailVerified = row[UserShelters.emailVerified],
            website = row[UserShelters.website],
            fiscalId = row[UserShelters.fiscalId],
            bankName = row[UserShelters.bankName],
            accountHolderName = row[UserShelters.accountHolderName],
            accountNumber = row[UserShelters.accountNumber],
            iban = row[UserShelters.iban],
            swiftBic = row[UserShelters.swiftBic],
            currency = row[UserShelters.currency],
            description = row[UserShelters.description],
            createdAt = row[UserShelters.createdAt]
        )
    }

    override suspend fun getByUserId(userId: Int): UserShelterDto? = withContext(dbDispatcher) {
        transaction {
            UserShelters.selectAll()
                .where { UserShelters.userId eq userId }
                .firstOrNull()
                ?.let { rowToDto(it) }
        }
    }

    override suspend fun create(userId: Int, request: CreateUserShelterRequest, emailVerified: Boolean): UserShelterDto {
        val now = clock.now().toEpochMilliseconds()
        val parsedCountry = Country.fromDisplayName(request.country)
            ?: throw IllegalArgumentException("Invalid country: ${request.country}")
        return withContext(dbDispatcher) {
            transaction {
                UserShelters.insert {
                    it[UserShelters.userId] = userId
                    it[UserShelters.name] = request.name
                    it[UserShelters.country] = parsedCountry
                    it[UserShelters.state] = request.state
                    it[UserShelters.city] = request.city
                    it[UserShelters.neighborhood] = request.neighborhood
                    it[UserShelters.address] = request.address
                    it[UserShelters.zip] = request.zip
                    it[UserShelters.phone] = request.phone
                    it[UserShelters.email] = request.email
                    it[UserShelters.emailVerified] = emailVerified
                    it[UserShelters.website] = request.website
                    it[UserShelters.fiscalId] = request.fiscalId
                    it[UserShelters.bankName] = request.bankName
                    it[UserShelters.accountHolderName] = request.accountHolderName
                    it[UserShelters.accountNumber] = request.accountNumber
                    it[UserShelters.iban] = request.iban
                    it[UserShelters.swiftBic] = request.swiftBic
                    it[UserShelters.currency] = request.currency
                    it[UserShelters.description] = request.description
                    it[UserShelters.createdAt] = now
                    it[UserShelters.updatedAt] = now
                }

                UserShelterDto(
                    userId = userId,
                    name = request.name,
                    country = parsedCountry.displayName,
                    state = request.state,
                    city = request.city,
                    neighborhood = request.neighborhood,
                    address = request.address,
                    zip = request.zip,
                    phone = request.phone,
                    email = request.email,
                    emailVerified = emailVerified,
                    website = request.website,
                    fiscalId = request.fiscalId,
                    bankName = request.bankName,
                    accountHolderName = request.accountHolderName,
                    accountNumber = request.accountNumber,
                    iban = request.iban,
                    swiftBic = request.swiftBic,
                    currency = request.currency,
                    description = request.description,
                    createdAt = now
                )
            }
        }
    }

    override suspend fun update(userId: Int, request: UpdateUserShelterRequest, emailVerifiedOverride: Boolean?): UserShelterDto? {
        val now = clock.now().toEpochMilliseconds()
        return withContext(dbDispatcher) {
            transaction {
                val existing = UserShelters.selectAll().where { UserShelters.userId eq userId }.firstOrNull()
                    ?: return@transaction null

                UserShelters.update({ UserShelters.userId eq userId }) { row ->
                    request.name?.let { row[UserShelters.name] = it }
                    request.country?.let {
                        row[UserShelters.country] = Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
                    }
                    request.state?.let { row[UserShelters.state] = it }
                    request.city?.let { row[UserShelters.city] = it }
                    request.neighborhood?.let { row[UserShelters.neighborhood] = it }
                    request.address?.let { row[UserShelters.address] = it }
                    request.zip?.let { row[UserShelters.zip] = it }
                    request.phone?.let { row[UserShelters.phone] = it }
                    request.email?.let { row[UserShelters.email] = it }
                    emailVerifiedOverride?.let { row[UserShelters.emailVerified] = it }
                    request.website?.let { row[UserShelters.website] = it }
                    request.fiscalId?.let { row[UserShelters.fiscalId] = it }
                    request.bankName?.let { row[UserShelters.bankName] = it }
                    request.accountHolderName?.let { row[UserShelters.accountHolderName] = it }
                    request.accountNumber?.let { row[UserShelters.accountNumber] = it }
                    request.iban?.let { row[UserShelters.iban] = it }
                    request.swiftBic?.let { row[UserShelters.swiftBic] = it }
                    request.currency?.let { row[UserShelters.currency] = it }
                    request.description?.let { row[UserShelters.description] = it }
                    row[UserShelters.updatedAt] = now
                }

                UserShelters.selectAll().where { UserShelters.userId eq userId }.firstOrNull()?.let { rowToDto(it) }
            }
        }
    }

    override suspend fun delete(userId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val rowsDeleted = UserShelters.deleteWhere { UserShelters.userId eq userId }
            rowsDeleted > 0
        }
    }

    override suspend fun search(country: String, state: String?, city: String?, neighborhood: String?, zip: String?): List<UserShelterDto> = withContext(dbDispatcher) {
        transaction {
            val parsedCountry = Country.fromDisplayName(country) ?: return@transaction emptyList()

            // Only publish shelters the owner has actually activated (which itself requires
            // both the account email and, when different, the shelter's own contact email to
            // be verified) - a saved-but-never-activated profile must not appear publicly.
            val activeShelterUserIds = UserActiveRoles.selectAll()
                .where { UserActiveRoles.role eq UserRole.SHELTER.name }
                .map { it[UserActiveRoles.userId] }
            if (activeShelterUserIds.isEmpty()) return@transaction emptyList()

            var conditions: Op<Boolean> = (UserShelters.country eq parsedCountry) and
                (UserShelters.userId inList activeShelterUserIds)

            if (!state.isNullOrBlank()) {
                conditions = conditions.and(UserShelters.state eq state)
            }
            if (!city.isNullOrBlank()) {
                conditions = conditions.and(UserShelters.city eq city)
            }
            if (!neighborhood.isNullOrBlank()) {
                conditions = conditions.and(UserShelters.neighborhood eq neighborhood)
            }
            if (!zip.isNullOrBlank()) {
                conditions = conditions.and(UserShelters.zip eq zip)
            }

            UserShelters.selectAll()
                .where { conditions }
                .map { rowToDto(it) }
        }
    }
}
