package com.adoptu.services.validation

import com.adoptu.dto.input.UserDto
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import com.adoptu.services.crypto.CryptoService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AuthValidationService : KoinComponent {

    private val userService: UserService by inject()

    fun validateAndDecryptEmail(encryptedData: String): ServiceResult<String> {
        val email = CryptoService.decrypt(encryptedData) 
            ?: return ServiceResult.Error("Failed to decrypt email")
        
        val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        if (!emailRegex.matches(email)) {
            return ServiceResult.Error("Invalid email format")
        }
        
        return ServiceResult.Success(email)
    }

    suspend fun validateEmailAndUser(email: String): ServiceResult<UserDto> {
        val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        if (!emailRegex.matches(email)) {
            return ServiceResult.Error("Invalid email format")
        }

        val user = userService.getByEmail(email)
        return if (user != null) ServiceResult.Success(user)
               else ServiceResult.Error("Invalid credentials")
    }

    suspend fun validateUserById(userId: Int): ServiceResult<UserDto> {
        val user = userService.getById(userId)
        return if (user != null) ServiceResult.Success(user)
               else ServiceResult.NotFound
    }

    suspend fun validateNotBanned(userId: Int): ServiceResult<UserDto> {
        if (userService.isBanned(userId)) {
            val user = userService.getById(userId)
            return if (user != null) ServiceResult.Error("Your account has been suspended. Reason: ${user.banReason ?: "Contact administrator"}")
                   else ServiceResult.NotFound
        }
        return validateUserById(userId)
    }

    suspend fun validateVerified(userId: Int, email: String): ServiceResult<Unit> {
        if (!userService.isUserVerified(userId)) {
            return ServiceResult.Error(email)
        }
        return ServiceResult.Success(Unit)
    }

    suspend fun getUserByEmail(email: String): UserDto? {
        return userService.getByEmail(email)
    }
}
