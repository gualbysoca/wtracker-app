package com.wtracker.field.utils

/**
 * Resultado explícito para operaciones que pueden fallar. Evita propagar excepciones
 * crudas hacia la UI y obliga a cada capa a decidir qué hacer con el error.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

/**
 * Taxonomía de errores de la app. `isRetryable` dirige la política de sincronización:
 * los errores transitorios se reintentan, los definitivos se marcan como rechazados.
 */
sealed interface AppError {
    val message: String
    val isRetryable: Boolean get() = false

    data class Network(
        override val message: String = "Sin conexión con el servidor. Se reintentará automáticamente.",
    ) : AppError {
        override val isRetryable: Boolean get() = true
    }

    data class Server(override val message: String, val httpCode: Int) : AppError {
        override val isRetryable: Boolean get() = true
    }

    data class Unauthorized(override val message: String) : AppError

    data class Forbidden(override val message: String) : AppError

    data class Rejected(
        override val message: String,
        val httpCode: Int,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
    ) : AppError

    data class Validation(override val message: String) : AppError

    data class Permission(override val message: String) : AppError

    data class Location(override val message: String) : AppError

    data class Camera(override val message: String) : AppError

    data class Storage(override val message: String) : AppError

    data class Unknown(override val message: String) : AppError
}
