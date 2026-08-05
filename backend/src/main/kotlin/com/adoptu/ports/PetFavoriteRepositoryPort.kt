package com.adoptu.ports

interface PetFavoriteRepositoryPort {
    /** No-op (not an error) if already favorited - idempotent, matches the unique(userId, petId) constraint. */
    suspend fun add(userId: Int, petId: Int)
    suspend fun remove(userId: Int, petId: Int): Boolean
    suspend fun getFavoritePetIds(userId: Int): List<Int>
    suspend fun isFavorited(userId: Int, petId: Int): Boolean
}
