package com.wtracker.field.validations

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ValidationResult
}
