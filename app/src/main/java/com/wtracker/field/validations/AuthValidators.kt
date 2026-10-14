package com.wtracker.field.validations

import com.wtracker.field.configs.VisitConfig

/** Espejo de `loginSchema` (Zod) del Backoffice para dar feedback inmediato sin red. */
object LoginFormValidator {

    const val FIELD_EMAIL = "email"
    const val FIELD_PASSWORD = "password"
    private const val PASSWORD_MIN_LENGTH = 6
    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validate(email: String, password: String): ValidationResult {
        val fieldErrors = buildMap {
            val normalizedEmail = email.trim()
            if (normalizedEmail.isBlank()) put(FIELD_EMAIL, "El email es requerido")
            else if (!EMAIL_PATTERN.matches(normalizedEmail)) put(FIELD_EMAIL, "El email no tiene un formato válido")

            if (password.length < PASSWORD_MIN_LENGTH) {
                put(FIELD_PASSWORD, "La contraseña debe tener al menos $PASSWORD_MIN_LENGTH caracteres")
            }
        }
        if (fieldErrors.isEmpty()) return ValidationResult.Valid
        return ValidationResult.Invalid(message = "Revisa los datos ingresados", fieldErrors = fieldErrors)
    }
}

/** La app de campo sólo admite roles operativos (mismo listado que `visit.service.createVisit`). */
object FieldRoleValidator {

    fun validate(role: String): ValidationResult {
        if (role in VisitConfig.FIELD_ROLES) return ValidationResult.Valid
        return ValidationResult.Invalid(
            "Esta aplicación es exclusiva para personal de campo. Usa el Backoffice web con tu rol \"$role\".",
        )
    }
}
