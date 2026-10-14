package com.wtracker.field.controllers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wtracker.field.services.auth.AuthService
import com.wtracker.field.utils.AppResult
import com.wtracker.field.validations.LoginFormValidator
import com.wtracker.field.validations.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

/** El éxito no navega aquí: la sesión guardada dispara la navegación global (AppStartViewModel). */
class LoginViewModel(private val authService: AuthService) : ViewModel() {

    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    fun onEmailChanged(email: String) {
        mutableUiState.update { it.copy(email = email, emailError = null, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        mutableUiState.update { it.copy(password = password, passwordError = null, errorMessage = null) }
    }

    fun onTogglePasswordVisibility() {
        mutableUiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSubmit() {
        val currentState = mutableUiState.value
        if (currentState.isSubmitting) return

        val validation = LoginFormValidator.validate(currentState.email, currentState.password)
        if (validation is ValidationResult.Invalid) {
            mutableUiState.update {
                it.copy(
                    emailError = validation.fieldErrors[LoginFormValidator.FIELD_EMAIL],
                    passwordError = validation.fieldErrors[LoginFormValidator.FIELD_PASSWORD],
                )
            }
            return
        }

        mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authService.login(currentState.email, currentState.password)
            mutableUiState.update { state ->
                when (result) {
                    is AppResult.Success -> state.copy(isSubmitting = false, password = "")
                    is AppResult.Failure -> state.copy(isSubmitting = false, errorMessage = result.error.message)
                }
            }
        }
    }
}
