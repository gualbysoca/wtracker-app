package com.wtracker.field.utils

import com.wtracker.field.services.api.dto.ApiErrorBodyDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Traduce excepciones de red/serialización al contrato de errores del Backoffice
 * (`{ success:false, message, errors }`) definido en `responseFormatter.js`.
 */
class ApiErrorMapper(private val json: Json) {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is HttpException -> fromHttpException(throwable)
        is SerializationException -> AppError.Unknown(UNEXPECTED_RESPONSE_MESSAGE)
        is IOException -> AppError.Network()
        else -> AppError.Unknown(throwable.message ?: UNKNOWN_ERROR_MESSAGE)
    }

    private fun fromHttpException(exception: HttpException): AppError {
        val httpCode = exception.code()
        val errorBody = parseErrorBody(exception)
        val message = errorBody?.message ?: "Error del servidor ($httpCode)"

        return when (httpCode) {
            HTTP_UNAUTHORIZED -> AppError.Unauthorized(message)
            HTTP_FORBIDDEN -> AppError.Forbidden(message)
            HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS -> AppError.Server(message, httpCode)
            in HTTP_SERVER_ERROR_RANGE -> AppError.Server(message, httpCode)
            else -> AppError.Rejected(message, httpCode, errorBody?.errors.orEmpty())
        }
    }

    private fun parseErrorBody(exception: HttpException): ApiErrorBodyDto? {
        val rawBody = exception.response()?.errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString(ApiErrorBodyDto.serializer(), rawBody) }.getOrNull()
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERROR_RANGE = 500..599
        const val UNEXPECTED_RESPONSE_MESSAGE = "Respuesta inesperada del servidor."
        const val UNKNOWN_ERROR_MESSAGE = "Ocurrió un error inesperado."
    }
}

/** Ejecuta una llamada remota y la convierte en [AppResult], respetando la cancelación de corrutinas. */
suspend fun <T> safeApiCall(errorMapper: ApiErrorMapper, block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppResult.Failure(errorMapper.map(throwable))
    }
