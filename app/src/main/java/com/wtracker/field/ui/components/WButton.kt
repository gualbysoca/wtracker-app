package com.wtracker.field.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.ui.theme.WTheme

enum class WButtonVariant { PRIMARY, SECONDARY, DANGER, SUCCESS, GHOST }

private data class WButtonColors(val container: Color, val content: Color)

@Composable
private fun resolveButtonColors(variant: WButtonVariant): WButtonColors {
    val colors = WTheme.colors
    return when (variant) {
        WButtonVariant.PRIMARY -> WButtonColors(colors.accent, colors.accentContent)
        WButtonVariant.SECONDARY -> WButtonColors(colors.surfaceElevated, colors.textPrimary)
        WButtonVariant.DANGER -> WButtonColors(colors.danger, colors.accentContent)
        WButtonVariant.SUCCESS -> WButtonColors(colors.statusVisited, colors.accentContent)
        WButtonVariant.GHOST -> WButtonColors(Color.Transparent, colors.textSecondary)
    }
}

@Composable
fun WButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WButtonVariant = WButtonVariant.PRIMARY,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val buttonColors = resolveButtonColors(variant)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) WTheme.alpha.pressedScale else 1f,
        label = "buttonPressScale",
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = WTheme.sizes.touchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(WTheme.radius.md),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = WTheme.spacing.lg, vertical = WTheme.spacing.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColors.container,
            contentColor = buttonColors.content,
            disabledContainerColor = buttonColors.container.copy(alpha = WTheme.alpha.disabled),
            disabledContentColor = buttonColors.content.copy(alpha = WTheme.alpha.disabled),
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(WTheme.sizes.iconSm),
                color = buttonColors.content,
                strokeWidth = WTheme.sizes.progressStroke,
            )
            return@Button
        }
        leadingIcon?.let {
            Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(WTheme.sizes.iconSm))
            Spacer(Modifier.width(WTheme.spacing.sm))
        }
        Text(text = text, style = WTheme.typography.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
