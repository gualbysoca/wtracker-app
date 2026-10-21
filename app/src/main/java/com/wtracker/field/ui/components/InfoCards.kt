package com.wtracker.field.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.StopEvent
import com.wtracker.field.services.models.StopEventType
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.TimeUtils
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Color,
    tooltipText: String,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    var showTooltip by remember { mutableStateOf(false) }

    if (showTooltip) {
        Dialog(onDismissRequest = { showTooltip = false }) {
            Surface(
                shape = RoundedCornerShape(WTheme.radius.lg),
                color = colors.surface,
                border = BorderStroke(WTheme.sizes.borderThin, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(WTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WTheme.spacing.lg)
                ) {
                    Box(
                        modifier = Modifier
                            .size(WTheme.sizes.iconXl)
                            .background(tone.copy(alpha = WTheme.alpha.subtle), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(WTheme.sizes.iconLg))
                    }
                    Text(text = label, style = WTheme.typography.title, color = colors.textPrimary)
                    Text(text = tooltipText, style = WTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    WButton(text = stringResource(R.string.action_back), onClick = { showTooltip = false }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    Surface(
        onClick = { showTooltip = true },
        modifier = modifier.heightIn(min = WTheme.sizes.metricTileMinHeight),
        shape = RoundedCornerShape(WTheme.radius.lg),
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.border),
    ) {
        Column(modifier = Modifier.padding(WTheme.spacing.lg)) {
            Box(
                modifier = Modifier.size(WTheme.sizes.iconLg).background(tone.copy(alpha = WTheme.alpha.subtle), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(WTheme.sizes.iconSm))
            }
            Spacer(Modifier.height(WTheme.spacing.sm))
            Text(text = value, style = WTheme.typography.metric, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = label, style = WTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StopEventRow(stopEvent: StopEvent, nowMillis: Long, zone: ZoneId, modifier: Modifier = Modifier) {
    val colors = WTheme.colors
    val isInactivity = stopEvent.type == StopEventType.INACTIVITY
    val tone = if (isInactivity) colors.danger else colors.warning
    val timeRange = buildString {
        append(TimeUtils.formatClock(stopEvent.startedAtMillis, zone))
        append(" – ")
        append(stopEvent.endedAtMillis?.let { TimeUtils.formatClock(it, zone) } ?: stringResource(R.string.stop_ongoing))
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = WTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isInactivity) Icons.Outlined.Bedtime else Icons.Outlined.PauseCircle,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(WTheme.sizes.iconMd),
        )
        Spacer(Modifier.width(WTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stopEvent.type.label, style = WTheme.typography.bodyStrong, color = colors.textPrimary)
            Text(text = timeRange, style = WTheme.typography.caption, color = colors.textSecondary)
        }
        Text(
            text = TimeUtils.formatDuration(stopEvent.durationMillis(nowMillis)),
            style = WTheme.typography.bodyStrong,
            color = tone,
        )
    }
}

@Composable
fun ClientInfoCard(
    client: FieldClient,
    modifier: Modifier = Modifier,
    isVendor: Boolean = false,
    onTakeOrder: (() -> Unit)? = null,
) {
    val colors = WTheme.colors
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WTheme.radius.lg),
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.border),
    ) {
        Column(modifier = Modifier.padding(WTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = client.businessName,
                    style = WTheme.typography.title,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(WTheme.spacing.sm))
                StatusChip(status = client.currentStatus)
            }
            Spacer(Modifier.height(WTheme.spacing.md))
            InfoLine(icon = Icons.Outlined.Person, text = client.contactName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.client_no_contact))
            InfoLine(
                icon = Icons.Outlined.Badge,
                text = client.clientCode?.takeIf { it.isNotBlank() }?.let { "Cód. cliente: $it" } ?: "Cód. cliente: Sin asignar",
            )
            InfoLine(icon = Icons.Outlined.Place, text = client.address ?: stringResource(R.string.client_no_address))
            client.phone?.let { InfoLine(icon = Icons.Outlined.Phone, text = it) }

            Spacer(Modifier.height(WTheme.spacing.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WTheme.spacing.xs)) {
                ClientActionButton(
                    text = stringResource(R.string.action_navigate),
                    icon = Icons.Outlined.Directions,
                    onClick = {
                        val lat = client.coordinate?.latitude
                        val lng = client.coordinate?.longitude
                        if (lat != null && lng != null) {
                            val uri = Uri.parse("google.navigation:q=$lat,$lng")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            intent.setPackage("com.google.android.apps.maps")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    },
                    enabled = client.coordinate != null,
                    modifier = Modifier.weight(1f)
                )
                ClientActionButton(
                    text = stringResource(R.string.action_call),
                    icon = Icons.Outlined.Phone,
                    onClick = {
                        client.phone?.let { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    },
                    enabled = client.phone != null,
                    modifier = Modifier.weight(1f)
                )
                ClientActionButton(
                    text = stringResource(R.string.action_whatsapp),
                    icon = Icons.Outlined.Message,
                    onClick = {
                        client.phone?.let { phone ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$phone"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    },
                    enabled = client.phone != null,
                    modifier = Modifier.weight(1f)
                )
                ClientActionButton(
                    text = stringResource(R.string.action_take_order_short),
                    icon = Icons.Outlined.ShoppingCart,
                    onClick = { onTakeOrder?.invoke() },
                    enabled = isVendor && onTakeOrder != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ClientActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(WTheme.radius.md),
        color = WTheme.colors.background,
        border = BorderStroke(WTheme.sizes.borderThin, WTheme.colors.border),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = WTheme.spacing.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (enabled) WTheme.colors.accent else WTheme.colors.textMuted,
                modifier = Modifier.size(WTheme.sizes.iconSm)
            )
            Spacer(Modifier.height(WTheme.spacing.xxs))
            Text(
                text = text,
                style = WTheme.typography.caption,
                color = if (enabled) WTheme.colors.textPrimary else WTheme.colors.textMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(modifier = Modifier.padding(vertical = WTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = WTheme.colors.textMuted, modifier = Modifier.size(WTheme.sizes.iconSm))
        Spacer(Modifier.width(WTheme.spacing.sm))
        Text(
            text = text,
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
