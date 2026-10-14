package com.wtracker.field.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wtracker.field.R
import com.wtracker.field.ui.theme.WTheme

/**
 * Modal de confirmación con el estilo de la app. Mientras la acción está en curso
 * ([isConfirmLoading]) no se puede descartar, para no perder el resultado de la operación.
 */
@Composable
fun WDialog(
    title: String,
    icon: ImageVector,
    tone: Color,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = stringResource(R.string.action_cancel),
    confirmVariant: WButtonVariant = WButtonVariant.PRIMARY,
    confirmIcon: ImageVector? = null,
    isConfirmLoading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing

    Dialog(
        onDismissRequest = { if (!isConfirmLoading) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isConfirmLoading, dismissOnClickOutside = !isConfirmLoading),
    ) {
        Surface(
            modifier = modifier.widthIn(max = WTheme.sizes.maxContentWidth).fillMaxWidth(),
            shape = RoundedCornerShape(WTheme.radius.xl),
            color = colors.surface,
            border = BorderStroke(WTheme.sizes.borderThin, colors.border),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                DialogIconBadge(icon = icon, tone = tone)
                Text(
                    text = title,
                    style = WTheme.typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.md), content = content)
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    WButton(
                        text = confirmText,
                        onClick = onConfirm,
                        variant = confirmVariant,
                        leadingIcon = confirmIcon,
                        isLoading = isConfirmLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WButton(
                        text = dismissText,
                        onClick = onDismiss,
                        variant = WButtonVariant.GHOST,
                        enabled = !isConfirmLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogIconBadge(icon: ImageVector, tone: Color) {
    Box(
        modifier = Modifier
            .size(WTheme.sizes.iconXl)
            .background(tone.copy(alpha = WTheme.alpha.subtle), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(WTheme.sizes.iconLg))
    }
}
