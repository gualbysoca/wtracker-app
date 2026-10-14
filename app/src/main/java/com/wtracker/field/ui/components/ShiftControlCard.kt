package com.wtracker.field.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import com.wtracker.field.R
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.TimeUtils

/**
 * Estado de la jornada (indicador en vivo + tiempo transcurrido). Es sólo informativa: la jornada
 * se abre con "Iniciar Ruta" y se cierra con "Finalizar Ruta".
 */
@Composable
fun ShiftControlCard(
    activeShiftStartedAtMillis: Long?,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    val isActive = activeShiftStartedAtMillis != null
    val shape = RoundedCornerShape(WTheme.radius.lg)
    val borderBrush = if (isActive) {
        Brush.linearGradient(listOf(colors.brandGradientStart, colors.brandGradientEnd))
    } else {
        Brush.linearGradient(listOf(colors.border, colors.border))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(WTheme.sizes.borderThin, borderBrush, shape)
            .padding(WTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveIndicator(isActive = isActive)
            Spacer(Modifier.width(WTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (isActive) R.string.shift_active_title else R.string.shift_inactive_title),
                    style = WTheme.typography.subtitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = activeShiftStartedAtMillis?.let { startedAt ->
                        stringResource(R.string.shift_elapsed, TimeUtils.formatDuration(nowMillis - startedAt))
                    } ?: stringResource(R.string.shift_inactive_message),
                    style = WTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun LiveIndicator(isActive: Boolean) {
    val colors = WTheme.colors
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = WTheme.alpha.disabled,
        animationSpec = infiniteRepeatable(tween(WTheme.motion.longMillis * 2), RepeatMode.Reverse),
        label = "livePulseAlpha",
    )
    Spacer(
        modifier = Modifier
            .size(WTheme.sizes.statusDot)
            .alpha(if (isActive) pulseAlpha else 1f)
            .background(if (isActive) colors.success else colors.statusPending, CircleShape),
    )
}

@Composable
fun RouteProgressHeader(completedCount: Int, totalCount: Int, modifier: Modifier = Modifier) {
    val colors = WTheme.colors
    val progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(WTheme.motion.longMillis),
        label = "routeProgress",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = completedCount.toString(), style = WTheme.typography.display, color = colors.textPrimary)
            Spacer(Modifier.width(WTheme.spacing.xs))
            Text(
                text = stringResource(R.string.route_progress_suffix, totalCount),
                style = WTheme.typography.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = WTheme.spacing.xs),
            )
        }
        Spacer(Modifier.height(WTheme.spacing.sm))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(WTheme.sizes.progressTrack)
                .clip(CircleShape),
            color = colors.success,
            trackColor = colors.surfaceElevated,
            strokeCap = StrokeCap.Round,
            gapSize = WTheme.spacing.none,
            drawStopIndicator = {},
        )
    }
}
