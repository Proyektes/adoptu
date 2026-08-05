package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.PetFavorites
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.ports.PetFavoriteRepositoryPort
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
class PetFavoriteRepositoryImpl(
    private val clock: Clock
) : PetFavoriteRepositoryPort {

    override suspend fun add(userId: Int, petId: Int): Unit = withContext(dbDispatcher) {
        transaction {
            val exists = PetFavorites.selectAll()
                .where { (PetFavorites.userId eq userId) and (PetFavorites.petId eq petId) }
                .any()
            if (!exists) {
                PetFavorites.insert {
                    it[PetFavorites.userId] = userId
                    it[PetFavorites.petId] = petId
                    it[PetFavorites.createdAt] = clock.now().toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun remove(userId: Int, petId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetFavorites.deleteWhere { (PetFavorites.userId eq userId) and (PetFavorites.petId eq petId) } > 0
        }
    }

    override suspend fun getFavoritePetIds(userId: Int): List<Int> = withContext(dbDispatcher) {
        transaction {
            PetFavorites.selectAll()
                .where { PetFavorites.userId eq userId }
                .map { it[PetFavorites.petId] }
        }
    }

    override suspend fun isFavorited(userId: Int, petId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetFavorites.selectAll()
                .where { (PetFavorites.userId eq userId) and (PetFavorites.petId eq petId) }
                .any()
        }
    }
}
