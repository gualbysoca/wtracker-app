package com.wtracker.field.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.services.models.RouteStop
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.GeoUtils

/**
 * [onRemove] se ofrece sólo en tareas pendientes (sin marcación alguna); null oculta la acción.
 * El botón de quitar aparece deslizando al mantener presionada la tarjeta y se oculta con un toque.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientRouteCard(stop: RouteStop, onClick: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val colors = WTheme.colors
    val client = stop.client
    val tone = client.currentStatus.toneColor()
    val shape = RoundedCornerShape(WTheme.radius.lg)
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) WTheme.alpha.pressedScale else 1f, label = "cardPressScale")
    val canRemove = onRemove != null && client.currentStatus == VisitStatus.PENDING
    var isRemoveRevealed by remember(client.id) { mutableStateOf(false) }
    val isRemoveVisible = canRemove && isRemoveRevealed

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { if (isRemoveVisible) isRemoveRevealed = false else onClick() },
                onLongClick = if (canRemove) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        isRemoveRevealed = true
                    }
                } else {
                    null
                },
            ),
        shape = shape,
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.border),
    ) {
        Row(modifier = Modifier.padding(WTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(WTheme.sizes.orderBadge)
                    .background(tone.copy(alpha = WTheme.alpha.subtle), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stop.order.toString(), style = WTheme.typography.bodyStrong, color = tone)
            }
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = client.address ?: stringResource(R.string.client_no_address),
                    style = WTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(WTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = client.currentStatus)
                    stop.distanceFromUserMeters?.let { distance ->
                        Spacer(Modifier.width(WTheme.spacing.sm))
                        Text(text = GeoUtils.formatDistance(distance), style = WTheme.typography.caption, color = colors.textMuted)
                    }
                    if (client.hasPendingSync) {
                        Spacer(Modifier.width(WTheme.spacing.sm))
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = stringResource(R.string.sync_pending_content_description),
                            tint = colors.warning,
                            modifier = Modifier.size(WTheme.sizes.iconSm),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = isRemoveVisible,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
            ) {
                FilledIconButton(
                    onClick = { onRemove?.invoke() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.danger,
                        contentColor = colors.accentContent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.route_remove_client_description),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
            )
        }
    }
}
