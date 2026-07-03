package com.adoptu.web

import com.adoptu.services.ServiceResult
import com.adoptu.services.validation.ValidationConstants
import io.helidon.http.HeaderNames
import io.helidon.http.Status
import io.helidon.webserver.http.ServerResponse

data class ErrorResponse(val error: String)
data class SuccessResponse(val success: Boolean)

fun ServerResponse.respondError(message: String, status: Int = 400) {
    status(status).send(ErrorResponse(error = message))
}

fun ServerResponse.respondUnauthorized() = respondError(ValidationConstants.UNAUTHORIZED, 401)
fun ServerResponse.respondForbidden() = respondError(ValidationConstants.FORBIDDEN, 403)
fun ServerResponse.respondNotFound(message: String = ValidationConstants.USER_NOT_FOUND) = respondError(message, 404)
fun ServerResponse.respondInvalidId(fieldName: String = ValidationConstants.INVALID_ID) = respondError(fieldName, 400)

fun ServerResponse.respondRedirect(location: String) {
    status(Status.FOUND_302).header(HeaderNames.LOCATION, location).send()
}

/** Sends [result].data as JSON on success, or the matching error status otherwise. */
fun ServerResponse.respondData(result: ServiceResult<*>) {
    when (result) {
        is ServiceResult.Success -> send(result.data as Any)
        is ServiceResult.NotFound -> respondError(ValidationConstants.NOT_FOUND, 404)
        is ServiceResult.Forbidden -> respondError("Forbidden", 403)
        is ServiceResult.Error -> respondError(result.message)
    }
}

/** Sends `{success: true}` on success, or the matching error status otherwise. */
fun ServerResponse.respondSuccess(result: ServiceResult<*>) {
    when (result) {
        is ServiceResult.Success -> send(SuccessResponse(success = true))
        is ServiceResult.NotFound -> respondError(ValidationConstants.NOT_FOUND, 404)
        is ServiceResult.Forbidden -> respondError("Forbidden", 403)
        is ServiceResult.Error -> respondError(result.message)
    }
}
