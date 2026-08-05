package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.SavedSearches
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.common.Country
import com.adoptu.dto.input.CreateSavedSearchRequest
import com.adoptu.dto.input.SavedSearchDto
import com.adoptu.ports.SavedSearchRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SavedSearchRepositoryImpl(
    private val clock: Clock
) : SavedSearchRepositoryPort {

    private fun rowToDto(row: org.jetbrains.exposed.v1.core.ResultRow): SavedSearchDto = SavedSearchDto(
        id = row[SavedSearches.id],
        userId = row[SavedSearches.userId],
        type = row[SavedSearches.type],
        country = row[SavedSearches.country].displayName,
        createdAt = row[SavedSearches.createdAt]
    )

    override suspend fun create(userId: Int, request: CreateSavedSearchRequest): SavedSearchDto = withContext(dbDispatcher) {
        val createdAt = clock.now().toEpochMilliseconds()
        val country = Country.fromDisplayName(request.country)
            ?: throw IllegalArgumentException("Invalid country: ${request.country}")
        transaction {
            val id = SavedSearches.insert {
                it[SavedSearches.userId] = userId
                it[SavedSearches.type] = request.type
                it[SavedSearches.country] = country
                it[SavedSearches.createdAt] = createdAt
            } get SavedSearches.id
            SavedSearchDto(id = id, userId = userId, type = request.type, country = country.displayName, createdAt = createdAt)
        }
    }

    override suspend fun getByUser(userId: Int): List<SavedSearchDto> = withContext(dbDispatcher) {
        transaction {
            SavedSearches.selectAll()
                .where { SavedSearches.userId eq userId }
                .map(::rowToDto)
        }
    }

    override suspend fun getById(id: Int): SavedSearchDto? = withContext(dbDispatcher) {
        transaction {
            SavedSearches.selectAll()
                .where { SavedSearches.id eq id }
                .firstOrNull()
                ?.let(::rowToDto)
        }
    }

    override suspend fun delete(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            SavedSearches.deleteWhere { SavedSearches.id eq id } > 0
        }
    }

    override suspend fun getMatching(type: String, country: String): List<SavedSearchDto> = withContext(dbDispatcher) {
        val countryEnum = Country.fromDisplayName(country) ?: return@withContext emptyList()
        transaction {
            SavedSearches.selectAll()
                .where { SavedSearches.country eq countryEnum }
                .map(::rowToDto)
                .filter { it.type == null || it.type == type }
        }
    }
}
