package com.wtracker.field.ui.components

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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.services.models.RouteClosureReason
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.TimeUtils
import java.time.ZoneId

/** Modal "¿Listo para comenzar tu ruta de hoy?" con fecha, hora de inicio y cantidad de clientes. */
@Composable
fun StartRouteDialog(
    startAtMillis: Long,
    clientCount: Int,
    zone: ZoneId,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WDialog(
        title = stringResource(R.string.route_start_dialog_title),
        icon = Icons.Outlined.PlayCircle,
        tone = WTheme.colors.accent,
        confirmText = stringResource(R.string.route_start_confirm),
        confirmIcon = Icons.Outlined.PlayArrow,
        isConfirmLoading = isSubmitting,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WTheme.colors.surfaceElevated, RoundedCornerShape(WTheme.radius.md))
                .padding(WTheme.spacing.md),
        ) {
            RouteDetailRow(
                icon = Icons.Outlined.Event,
                label = stringResource(R.string.route_start_dialog_date),
                value = TimeUtils.formatLongDate(startAtMillis, zone),
            )
            RouteDetailRow(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.route_start_dialog_time),
                value = TimeUtils.formatClock(startAtMillis, zone),
            )
            RouteDetailRow(
                icon = Icons.Outlined.Storefront,
                label = stringResource(R.string.route_start_dialog_clients),
                value = clientCount.toString(),
            )
        }
    }
}

/**
 * Confirmación de cierre de ruta (al finalizar o al cerrar sesión): advierte que las visitas no
 * completadas quedarán como fallidas.
 */
@Composable
fun RouteClosureDialog(
    reason: RouteClosureReason,
    pendingVisitCount: Int,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WTheme.colors
    val hasPendingVisits = pendingVisitCount > 0
    val isLogout = reason == RouteClosureReason.LOGOUT
    val confirmIcon = if (isLogout) Icons.AutoMirrored.Outlined.Logout else Icons.Outlined.Flag

    WDialog(
        title = stringResource(if (isLogout) R.string.route_logout_dialog_title else R.string.route_finish_dialog_title),
        icon = confirmIcon,
        tone = if (hasPendingVisits) colors.warning else colors.success,
        confirmText = stringResource(if (isLogout) R.string.action_logout else R.string.route_finish_action),
        confirmIcon = confirmIcon,
        confirmVariant = if (hasPendingVisits) WButtonVariant.DANGER else WButtonVariant.PRIMARY,
        isConfirmLoading = isSubmitting,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        if (isLogout) {
            DialogBodyText(text = stringResource(R.string.route_logout_closes_route_message))
        }
        DialogBodyText(
            text = if (hasPendingVisits) {
                pluralStringResource(R.plurals.route_finish_pending_message, pendingVisitCount, pendingVisitCount)
            } else {
                stringResource(R.string.route_finish_all_done_message)
            },
        )
        if (hasPendingVisits) {
            InlineMessage(message = stringResource(R.string.route_finish_failed_warning), tone = MessageTone.WARNING)
        }
    }
}

@Composable
private fun DialogBodyText(text: String) {
    Text(
        text = text,
        style = WTheme.typography.body,
        color = WTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RouteDetailRow(icon: ImageVector, label: String, value: String) {
    val colors = WTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = WTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(WTheme.sizes.iconLg)
                .background(colors.accent.copy(alpha = WTheme.alpha.subtle), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(WTheme.sizes.iconSm))
        }
        Spacer(Modifier.width(WTheme.spacing.md))
        Text(text = label, style = WTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(WTheme.spacing.sm))
        Text(
            text = value,
            style = WTheme.typography.bodyStrong,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
