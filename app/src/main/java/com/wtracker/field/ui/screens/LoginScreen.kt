package com.wtracker.field.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wtracker.field.R
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.controllers.ControllerFactories
import com.wtracker.field.controllers.LoginUiState
import com.wtracker.field.controllers.LoginViewModel
import com.wtracker.field.ui.components.BrandMark
import com.wtracker.field.ui.components.GradientBackground
import com.wtracker.field.ui.components.InlineMessage
import com.wtracker.field.ui.components.MessageTone
import com.wtracker.field.ui.components.WButton
import com.wtracker.field.ui.components.WTextField
import com.wtracker.field.ui.theme.WTheme

/** Conecta el controlador con la UI. La navegación post-login la decide AppStartViewModel. */
@Composable
fun LoginRoute(container: AppContainer) {
    val viewModel: LoginViewModel = viewModel(factory = ControllerFactories.login(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        uiState = uiState,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val spacing = WTheme.spacing

    GradientBackground {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .widthIn(max = WTheme.sizes.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            BrandMark()
            LoginHeader()
            Spacer(Modifier.height(spacing.sm))

            WTextField(
                value = uiState.email,
                onValueChange = onEmailChanged,
                label = stringResource(R.string.login_email_label),
                leadingIcon = Icons.Outlined.Email,
                errorMessage = uiState.emailError,
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            WTextField(
                value = uiState.password,
                onValueChange = onPasswordChanged,
                label = stringResource(R.string.login_password_label),
                leadingIcon = Icons.Outlined.Lock,
                errorMessage = uiState.passwordError,
                enabled = !uiState.isSubmitting,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                }),
                trailingContent = {
                    PasswordVisibilityToggle(isVisible = uiState.isPasswordVisible, onToggle = onTogglePasswordVisibility)
                },
            )

            uiState.errorMessage?.let { InlineMessage(message = it, tone = MessageTone.ERROR) }

            WButton(
                text = stringResource(R.string.login_submit),
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                leadingIcon = Icons.AutoMirrored.Outlined.Login,
                isLoading = uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.login_footer),
                style = WTheme.typography.caption,
                color = WTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoginHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WTheme.spacing.xs)) {
        Text(text = stringResource(R.string.login_title), style = WTheme.typography.display, color = WTheme.colors.textPrimary)
        Text(
            text = stringResource(R.string.login_subtitle),
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PasswordVisibilityToggle(isVisible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(if (isVisible) R.string.login_hide_password else R.string.login_show_password),
        )
    }
}
