package com.adoptu.services.validation

import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.services.ServiceResult
import com.adoptu.services.TemporalHomeService
import com.adoptu.services.UserService
import com.adoptu.services.auth.SessionUser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TemporalHomesValidationService : KoinComponent {

    private val userService: UserService by inject()
    private val temporalHomeService: TemporalHomeService by inject()

    fun validateSession(session: SessionUser?): ServiceResult<SessionUser> {
        return if (session != null) ServiceResult.Success(session) 
               else ServiceResult.Forbidden
    }

    suspend fun validateUserById(userId: Int): ServiceResult<UserDto> {
        val user = userService.getById(userId)
        return if (user != null) ServiceResult.Success(user)
               else ServiceResult.NotFound
    }

    fun validateUser(user: UserDto?): ServiceResult<UserDto> {
        return if (user != null) ServiceResult.Success(user) 
               else ServiceResult.NotFound
    }

    fun validateId(id: String?): ServiceResult<Int> {
        val parsedId = id?.toIntOrNull()
        return if (parsedId != null) ServiceResult.Success(parsedId) 
               else ServiceResult.Error(ValidationConstants.INVALID_ID)
    }

    fun validateRequired(value: String?, fieldName: String): ServiceResult<String> {
        return if (!value.isNullOrBlank()) ServiceResult.Success(value)
               else ServiceResult.Error("$fieldName is required")
    }

    fun validateRole(user: UserDto, requiredRole: String): ServiceResult<Unit> {
        val activeRoles = user.activeRoles.map { it.name }
        return if (activeRoles.contains(requiredRole) || activeRoles.contains("ADMIN")) 
            ServiceResult.Success(Unit) 
        else 
            ServiceResult.Forbidden
    }

    suspend fun validateCreateTemporalHomeRequest(userId: Int, body: CreateTemporalHomeRequest): ServiceResult<CreateTemporalHomeRequest> {
        val existing = temporalHomeService.getTemporalHome(userId)
        if (existing != null) {
            return ServiceResult.Error(ValidationConstants.TEMPORAL_HOME_PROFILE_ALREADY_EXISTS)
        }

        val aliasResult = validateRequired(body.alias, "Alias")
        if (aliasResult is ServiceResult.Error) return ServiceResult.Error(aliasResult.message)

        val countryResult = validateRequired(body.country, "Country")
        if (countryResult is ServiceResult.Error) return ServiceResult.Error(countryResult.message)

        val cityResult = validateRequired(body.city, "City")
        if (cityResult is ServiceResult.Error) return ServiceResult.Error(cityResult.message)

        return ServiceResult.Success(body)
    }

    suspend fun validateTemporalHomeProfile(userId: Int): ServiceResult<Unit> {
        val existing = temporalHomeService.getTemporalHome(userId)
        return if (existing != null) ServiceResult.Success(Unit)
        else ServiceResult.Error(ValidationConstants.TEMPORAL_HOME_PROFILE_NOT_FOUND)
    }

    suspend fun validateRescuerRole(userId: Int): ServiceResult<UserDto> {
        val user = userService.getById(userId) ?: return ServiceResult.NotFound
        val activeRoles = user.activeRoles.map { it.name }
        return if (activeRoles.contains("RESCUER") || activeRoles.contains("ADMIN")) ServiceResult.Success(user)
        else ServiceResult.Error(ValidationConstants.ONLY_RESCUERS_CAN_SEND_REQUESTS)
    }

    suspend fun validateBlockRescuerRequest(userId: Int): ServiceResult<UserDto> {
        val user = userService.getById(userId) ?: return ServiceResult.NotFound
        val activeRoles = user.activeRoles.map { it.name }
        return if (activeRoles.contains("TEMPORAL_HOME") || activeRoles.contains("ADMIN")) ServiceResult.Success(user)
        else ServiceResult.Forbidden
    }

    fun validateTemporalHomeId(id: String?): ServiceResult<Int> {
        val parsedId = id?.toIntOrNull()
        return if (parsedId != null) ServiceResult.Success(parsedId)
        else ServiceResult.Error(ValidationConstants.INVALID_TEMPORAL_HOME_ID)
    }
}
