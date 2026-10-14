package com.wtracker.field.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.ui.theme.WTheme

/** Fila de cliente seleccionable con un toque: se resalta y muestra un check cuando está marcada. */
@Composable
fun SelectableClientCard(
    client: FieldClient,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = WTheme.colors
    val animationSpec = tween<Color>(WTheme.motion.shortMillis)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent.copy(alpha = WTheme.alpha.subtle).compositeOver(colors.surface) else colors.surface,
        animationSpec = animationSpec,
        label = "selectableContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.border,
        animationSpec = animationSpec,
        label = "selectableBorder",
    )

    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().semantics { selected = isSelected },
        shape = RoundedCornerShape(WTheme.radius.lg),
        color = containerColor,
        border = BorderStroke(if (isSelected) WTheme.sizes.borderThick else WTheme.sizes.borderThin, borderColor),
    ) {
        Row(modifier = Modifier.padding(WTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            ClientAvatar(isSelected = isSelected)
            Spacer(Modifier.width(WTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.businessName,
                    style = WTheme.typography.subtitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = client.contactName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.client_no_contact),
                    style = WTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(WTheme.spacing.sm))
            SelectionIndicator(isSelected = isSelected)
        }
    }
}

@Composable
private fun ClientAvatar(isSelected: Boolean) {
    val colors = WTheme.colors
    val tone = if (isSelected) colors.accent else colors.textMuted
    Box(
        modifier = Modifier
            .size(WTheme.sizes.orderBadge)
            .background(tone.copy(alpha = WTheme.alpha.subtle), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Outlined.Storefront, contentDescription = null, tint = tone, modifier = Modifier.size(WTheme.sizes.iconSm))
    }
}

@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    val colors = WTheme.colors
    val durationMillis = WTheme.motion.shortMillis
    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            (fadeIn(tween(durationMillis)) + scaleIn(tween(durationMillis)))
                .togetherWith(fadeOut(tween(durationMillis)))
        },
        label = "selectionIndicator",
    ) { selected ->
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = stringResource(if (selected) R.string.client_selected_description else R.string.client_not_selected_description),
            tint = if (selected) colors.accent else colors.textMuted,
            modifier = Modifier.size(WTheme.sizes.iconMd),
        )
    }
}
