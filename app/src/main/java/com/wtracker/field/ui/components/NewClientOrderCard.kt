package com.wtracker.field.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.wtracker.field.ui.theme.WTheme

/**
 * Tarjeta accesible para tomar un pedido directo a un cliente nuevo que aún no está en la base de datos.
 * Exclusiva para perfiles con rol de vendedor.
 */
@Composable
fun NewClientOrderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing
    val shape = RoundedCornerShape(WTheme.radius.lg)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) WTheme.alpha.pressedScale else 1f, label = "newClientCardScale")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = shape,
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(WTheme.sizes.iconXl)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = WTheme.alpha.subtle)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonAddAlt,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(WTheme.sizes.iconMd),
                )
            }

            Spacer(Modifier.width(spacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = "Tomar Pedido - Cliente Nuevo",
                    style = WTheme.typography.subtitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Registra un pedido para un cliente que no está en la base de datos",
                    style = WTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.width(spacing.sm))

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.accent,
            )
        }
    }
}
