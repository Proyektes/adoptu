package com.adoptu.services.validation

import com.adoptu.dto.input.UserDto
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UsersValidationService : KoinComponent {

    private val userService: UserService by inject()

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

    fun validateNotSelf(currentUserId: Int, targetUserId: Int): ServiceResult<Unit> {
        return if (currentUserId != targetUserId) ServiceResult.Success(Unit)
               else ServiceResult.Error("Cannot perform action on yourself")
    }

    fun validateNotAdmin(user: UserDto?): ServiceResult<Unit> {
        if (user == null) return ServiceResult.NotFound
        val isAdmin = user.activeRoles.any { it.name == "ADMIN" }
        return if (!isAdmin) ServiceResult.Success(Unit)
               else ServiceResult.Error("Cannot perform action on an admin")
    }
}
