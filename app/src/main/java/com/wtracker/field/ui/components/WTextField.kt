package com.wtracker.field.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import com.wtracker.field.ui.theme.WTheme

@Composable
fun WTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    enabled: Boolean = true,
) {
    val colors = WTheme.colors

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> if (maxLength == null || input.length <= maxLength) onValueChange(input) },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            label = { Text(text = label, style = WTheme.typography.body) },
            leadingIcon = leadingIcon?.let { icon -> { Icon(imageVector = icon, contentDescription = null) } },
            trailingIcon = trailingContent,
            isError = errorMessage != null,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = WTheme.typography.body,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(WTheme.radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textSecondary,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface.copy(alpha = 0.5f),
                errorContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                disabledBorderColor = colors.border.copy(alpha = 0.5f),
                errorBorderColor = colors.danger,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textSecondary,
                disabledLabelColor = colors.textMuted,
                cursorColor = colors.accent,
                focusedLeadingIconColor = colors.accent,
                unfocusedLeadingIconColor = colors.textMuted,
                disabledLeadingIconColor = colors.textMuted.copy(alpha = 0.6f),
                focusedTrailingIconColor = colors.textSecondary,
                unfocusedTrailingIconColor = colors.textMuted,
                disabledTrailingIconColor = colors.textMuted.copy(alpha = 0.6f),
            ),
        )
        AnimatedVisibility(visible = errorMessage != null || supportingText != null) {
            Text(
                text = errorMessage ?: supportingText.orEmpty(),
                style = WTheme.typography.caption,
                color = if (errorMessage != null) colors.danger else colors.textMuted,
                modifier = Modifier.padding(start = WTheme.spacing.md, top = WTheme.spacing.xs),
            )
        }
    }
}
