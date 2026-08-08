package com.adoptu.services

import com.adoptu.dto.input.AcceptTermsRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.PagedResult
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.universaliun.auth.backend.domain.port.out.UserRepositoryPort as KitUserRepositoryPort
import com.universaliun.auth.common.identity.AuthUserId

class UserService(
    private val userRepository: UserRepositoryPort,
    private val photographerRepository: PhotographerRepositoryPort,
    // Account activation state (deactivatedAt/deactivatedBy on the shared Users table) is owned
    // by AuthKit's own UserRepositoryPort now -- see AdoptuUserRepositoryAdapter.deactivate/
    // reactivate. Adoptu's native UserRepositoryPort used to have its own competing
    // deactivateUser/reactivateUser methods writing the same columns through a separate code
    // path; that duplication was removed so there is exactly one writer of this state.
    // isBanned/banReason stay on the native port below -- AuthKit's AuthUser model has no concept
    // of banning, so there is nothing to consolidate there.
    private val kitUserRepository: KitUserRepositoryPort,
) {
    suspend fun getById(userId: Int): UserDto? = userRepository.getById(userId)

    suspend fun getByEmail(email: String): UserDto? = userRepository.getByEmail(email)

    suspend fun getAllUsers(
        page: Int = 1,
        pageSize: Int = 20,
        role: UserRole? = null,
        search: String? = null,
        includeInactive: Boolean = false,
        includeBanned: Boolean = false
    ): PagedResult<UserDto> = userRepository.getAllUsers(page, pageSize, role, search, includeInactive, includeBanned)
    
    suspend fun getRescuers(): List<UserDto> = userRepository.getRescuers()
    
    suspend fun banUser(userId: Int, reason: String? = null): Boolean = userRepository.banUser(userId, reason)
    
    suspend fun unbanUser(userId: Int): Boolean = userRepository.unbanUser(userId)

    // Existence is checked against the native port first (cheap read, no behavior change for
    // callers) since AuthKit's deactivate/reactivate are fire-and-forget updates that don't
    // report whether a row actually matched - callers (UsersRoutes.kt) rely on a false return
    // here to surface a 404/500 for a non-existent target.
    suspend fun deactivateUser(userId: Int, deactivatedBy: Int): Boolean {
        if (userRepository.getById(userId) == null) return false
        kitUserRepository.deactivate(AuthUserId(userId.toString()), AuthUserId(deactivatedBy.toString()))
        return true
    }

    suspend fun reactivateUser(userId: Int): Boolean {
        if (userRepository.getById(userId) == null) return false
        kitUserRepository.reactivate(AuthUserId(userId.toString()))
        return true
    }
    
    suspend fun isBanned(userId: Int): Boolean = userRepository.isBanned(userId)
    
    suspend fun isRoleActive(userId: Int, role: UserRole): Boolean =
        userRepository.isRoleActive(userId, role)
    
    suspend fun activateRescuerProfile(userId: Int): UserDto? = userRepository.activateRescuerProfile(userId)
    
    suspend fun deactivateRescuerProfile(userId: Int): UserDto? = userRepository.deactivateRescuerProfile(userId)
    
    suspend fun activateTemporalHomeProfile(userId: Int): UserDto? = userRepository.activateTemporalHomeProfile(userId)
    
    suspend fun deactivateTemporalHomeProfile(userId: Int): UserDto? = userRepository.deactivateTemporalHomeProfile(userId)
    
    suspend fun activateShelterProfile(userId: Int): UserDto? = userRepository.activateShelterProfile(userId)
    
    suspend fun deactivateShelterProfile(userId: Int): UserDto? = userRepository.deactivateShelterProfile(userId)
    
    suspend fun activateSterilizationProfile(userId: Int): UserDto? = userRepository.activateSterilizationProfile(userId)
    
    suspend fun deactivateSterilizationProfile(userId: Int): UserDto? = userRepository.deactivateSterilizationProfile(userId)
    
    suspend fun updateProfile(userId: Int, displayName: String, language: String? = null, country: String? = null): UserDto? =
        userRepository.updateProfile(userId, displayName, language, country)
    
    suspend fun updateLanguage(userId: Int, language: String): UserDto? = userRepository.updateLanguage(userId, language)
    
    suspend fun acceptTerms(userId: Int, request: AcceptTermsRequest): UserDto? = userRepository.acceptTerms(userId, request)
    
    suspend fun isUserVerified(userId: Int): Boolean = userRepository.isEmailVerified(userId)
    
    suspend fun verifyToken(token: String): Boolean {
        val userId = userRepository.verifyToken(token) ?: return false
        val updated = userRepository.setEmailVerified(userId, true)
        if (updated) {
            userRepository.deleteVerificationTokens(userId)
            activatePendingRoles(userId)
        }
        return updated
    }

    suspend fun verifyTokenAndGetLanguage(token: String): Pair<Boolean, String> {
        val userId = userRepository.verifyToken(token) ?: return false to "en"
        val user = userRepository.getById(userId)
        val language = user?.language ?: "en"
        val updated = userRepository.setEmailVerified(userId, true)
        if (updated) {
            userRepository.deleteVerificationTokens(userId)
            activatePendingRoles(userId)
        }
        return updated to language
    }

    // Roles selected at registration for types that require a verified email
    // (see ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION in WebAuthnService.kt) were recorded
    // as pending instead of granted. Now that verification succeeded, grant them for real.
    // internal, not private: AuthRoutes.kt's /api/auth/verify-email also needs this for
    // AuthKit-gated signups, which verify against Users.resetTokenHash directly instead of going
    // through this class's own verifyToken()/verifyTokenAndGetLanguage().
    internal suspend fun activatePendingRoles(userId: Int) {
        val pendingRoles = userRepository.consumePendingRoleActivations(userId)
        pendingRoles.forEach { role ->
            when (role) {
                UserRole.RESCUER -> userRepository.activateRescuerProfile(userId)
                UserRole.TEMPORAL_HOME -> userRepository.activateTemporalHomeProfile(userId)
                UserRole.SHELTER -> userRepository.activateShelterProfile(userId)
                UserRole.STERILIZATION_SERVICE -> userRepository.activateSterilizationProfile(userId)
                UserRole.PHOTOGRAPHER -> photographerRepository.activatePhotographerProfile(userId)
                UserRole.URGENT_RESCUER -> userRepository.activateUrgentRescuerProfile(userId)
                else -> Unit
            }
        }
    }
}
