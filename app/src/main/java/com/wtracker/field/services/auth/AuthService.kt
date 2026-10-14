package com.wtracker.field.services.auth

import com.wtracker.field.services.api.WTrackerApi
import com.wtracker.field.services.api.dto.LoginRequestDto
import com.wtracker.field.services.api.dto.LoginResponseDto
import com.wtracker.field.services.api.dto.LogoutRequestDto
import com.wtracker.field.services.api.requireData
import com.wtracker.field.services.models.UserSession
import com.wtracker.field.services.sync.SyncScheduler
import com.wtracker.field.utils.ApiErrorMapper
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import com.wtracker.field.utils.safeApiCall
import com.wtracker.field.validations.FieldRoleValidator
import com.wtracker.field.validations.ValidationResult

class AuthService(
    private val api: WTrackerApi,
    private val sessionStore: SessionStore,
    private val syncScheduler: SyncScheduler,
    private val errorMapper: ApiErrorMapper,
) {

    suspend fun login(email: String, password: String): AppResult<UserSession> {
        val loginResult = safeApiCall(errorMapper) {
            api.login(LoginRequestDto(email = email.trim().lowercase(), password = password)).requireData()
        }
        if (loginResult is AppResult.Failure) return loginResult

        val loginResponse = (loginResult as AppResult.Success).data
        val roleValidation = FieldRoleValidator.validate(loginResponse.user.role)
        if (roleValidation is ValidationResult.Invalid) {
            revokeRefreshTokenQuietly(loginResponse.refreshToken)
            return AppResult.Failure(AppError.Forbidden(roleValidation.message))
        }

        val session = loginResponse.toUserSession()
        sessionStore.saveSession(session)
        syncScheduler.requestImmediateSync()
        syncScheduler.schedulePeriodicSync()
        return AppResult.Success(session)
    }

    /** Cierra sesión localmente aunque el servidor no responda: el usuario nunca queda bloqueado. */
    suspend fun logout() {
        sessionStore.ensureLoaded()?.let { revokeRefreshTokenQuietly(it.refreshToken) }
        sessionStore.clearSession()
        syncScheduler.cancelPeriodicSync()
    }

    private suspend fun revokeRefreshTokenQuietly(refreshToken: String) {
        // Revocar el token es una optimización de seguridad del servidor; su fallo no debe bloquear al usuario.
        safeApiCall(errorMapper) { api.logout(LogoutRequestDto(refreshToken)) }
    }

    private fun LoginResponseDto.toUserSession() = UserSession(
        userId = user.id,
        fullName = user.fullName,
        email = user.email,
        role = user.role,
        accessToken = accessToken,
        refreshToken = refreshToken,
    )
}
