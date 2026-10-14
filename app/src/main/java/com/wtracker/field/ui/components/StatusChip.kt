package com.wtracker.field.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wtracker.field.R
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.ui.theme.WTheme

/** Presentación visual de cada estado (color, ícono, texto) en un único lugar. */
@Composable
fun VisitStatus.toneColor(): Color = when (this) {
    VisitStatus.PENDING -> WTheme.colors.statusPending
    VisitStatus.EN_ROUTE -> WTheme.colors.statusEnRoute
    VisitStatus.VISITED -> WTheme.colors.statusVisited
    VisitStatus.FAILED -> WTheme.colors.statusFailed
}

@Composable
fun VisitStatus.displayLabel(): String = stringResource(
    when (this) {
        VisitStatus.PENDING -> R.string.status_pending
        VisitStatus.EN_ROUTE -> R.string.status_en_route
        VisitStatus.VISITED -> R.string.status_visited
        VisitStatus.FAILED -> R.string.status_failed
    },
)

val VisitStatus.icon: ImageVector
    get() = when (this) {
        VisitStatus.PENDING -> Icons.Outlined.Schedule
        VisitStatus.EN_ROUTE -> Icons.Outlined.DirectionsWalk
        VisitStatus.VISITED -> Icons.Outlined.CheckCircle
        VisitStatus.FAILED -> Icons.Outlined.Cancel
    }

@Composable
fun StatusChip(status: VisitStatus, modifier: Modifier = Modifier) {
    val tone by animateColorAsState(
        targetValue = status.toneColor(),
        animationSpec = tween(WTheme.motion.mediumMillis),
        label = "statusChipTone",
    )
    Row(
        modifier = modifier
            .background(tone.copy(alpha = WTheme.alpha.subtle), CircleShape)
            .border(WTheme.sizes.borderThin, tone.copy(alpha = WTheme.alpha.border), CircleShape)
            .padding(horizontal = WTheme.spacing.sm, vertical = WTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(WTheme.sizes.statusDot).background(tone, CircleShape))
        Spacer(Modifier.width(WTheme.spacing.xs))
        Text(text = status.displayLabel(), style = WTheme.typography.label, color = tone, maxLines = 1)
    }
}
