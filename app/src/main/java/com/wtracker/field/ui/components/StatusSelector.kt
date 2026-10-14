package com.wtracker.field.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.wtracker.field.R
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.ui.theme.WTheme

/** Seleccionador de estado: "en ruta", "visitado" o "fallido". */
@Composable
fun StatusSelector(
    selectedStatus: VisitStatus?,
    onStatusSelected: (VisitStatus) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WTheme.spacing.sm)) {
        VisitStatus.markableStatuses.forEach { status ->
            StatusOption(
                status = status,
                isSelected = status == selectedStatus,
                onClick = { onStatusSelected(status) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatusOption(
    status: VisitStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    val tone = status.toneColor()
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) tone else colors.border,
        animationSpec = tween(WTheme.motion.shortMillis),
        label = "statusOptionBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) tone.copy(alpha = WTheme.alpha.subtle) else colors.surface,
        animationSpec = tween(WTheme.motion.shortMillis),
        label = "statusOptionContainer",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            role = Role.RadioButton
            selected = isSelected
        },
        shape = RoundedCornerShape(WTheme.radius.md),
        color = containerColor,
        border = BorderStroke(if (isSelected) WTheme.sizes.borderThick else WTheme.sizes.borderThin, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(vertical = WTheme.spacing.md, horizontal = WTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = null,
                tint = if (isSelected) tone else colors.textSecondary,
                modifier = Modifier.size(WTheme.sizes.iconMd),
            )
            Spacer(Modifier.height(WTheme.spacing.xs))
            Text(
                text = status.displayLabel(),
                style = WTheme.typography.bodyStrong,
                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = if (status.requiresEvidence) stringResource(R.string.status_requires_photo) else stringResource(R.string.status_no_photo),
                style = WTheme.typography.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
