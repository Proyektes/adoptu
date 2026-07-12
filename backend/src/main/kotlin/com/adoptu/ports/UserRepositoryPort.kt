package com.adoptu.ports

import com.adoptu.dto.input.AcceptTermsRequest
import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.PagedResult

interface UserRepositoryPort {
    suspend fun getById(userId: Int): UserDto?
    suspend fun getByEmail(email: String): UserDto?
    // No default param values here: every call site is UserService.getAllUsers(), which
    // already declares its own defaults and always passes explicit args through - defaults
    // on this interface would just be unreachable synthetic dispatch bytecode.
    suspend fun getAllUsers(
        page: Int,
        pageSize: Int,
        role: UserRole?,
        search: String?,
        includeInactive: Boolean,
        includeBanned: Boolean
    ): PagedResult<UserDto>
    suspend fun getPhotographers(country: String? = null, state: String? = null): List<PhotographerDto>
    suspend fun getRescuers(): List<UserDto>
    suspend fun banUser(userId: Int, reason: String?): Boolean
    suspend fun unbanUser(userId: Int): Boolean
    suspend fun deactivateUser(userId: Int, deactivatedBy: Int): Boolean
    suspend fun reactivateUser(userId: Int): Boolean
    suspend fun isBanned(userId: Int): Boolean
    suspend fun isRoleActive(userId: Int, role: UserRole): Boolean
    suspend fun activateRescuerProfile(userId: Int): UserDto?
    suspend fun deactivateRescuerProfile(userId: Int): UserDto?
    suspend fun activateTemporalHomeProfile(userId: Int): UserDto?
    suspend fun deactivateTemporalHomeProfile(userId: Int): UserDto?
    suspend fun activateShelterProfile(userId: Int): UserDto?
    suspend fun deactivateShelterProfile(userId: Int): UserDto?
    suspend fun activateSterilizationProfile(userId: Int): UserDto?
    suspend fun deactivateSterilizationProfile(userId: Int): UserDto?
    suspend fun updateProfile(userId: Int, displayName: String, language: String?, country: String?): UserDto?
    suspend fun updateLanguage(userId: Int, language: String): UserDto?
    suspend fun updatePhotographerSettings(userId: Int, request: PhotographerSettingsRequest): PhotographerDto?
    suspend fun acceptTerms(userId: Int, request: AcceptTermsRequest): UserDto?
    suspend fun isEmailVerified(userId: Int): Boolean
    suspend fun setEmailVerified(userId: Int, verified: Boolean): Boolean
    suspend fun createEmailVerificationToken(userId: Int, token: String, expiresAt: Long): Boolean
    suspend fun verifyToken(token: String): Int?
    suspend fun getUserIdByToken(token: String): Int?
    suspend fun deleteVerificationTokens(userId: Int)
    suspend fun getVerificationAttemptsToday(userId: Int): Int
    suspend fun recordVerificationAttempt(userId: Int)
    suspend fun getLatestVerificationToken(userId: Int): EmailVerificationTokenInfo?
}
