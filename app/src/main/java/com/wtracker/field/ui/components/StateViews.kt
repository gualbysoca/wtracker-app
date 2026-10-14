package com.wtracker.field.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.ui.theme.WTheme

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(WTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = WTheme.colors.accent, strokeWidth = WTheme.sizes.progressStroke)
        Spacer(Modifier.height(WTheme.spacing.lg))
        Text(text = message, style = WTheme.typography.body, color = WTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StateMessage(
        icon = Icons.Outlined.CloudOff,
        tint = WTheme.colors.danger,
        title = stringResource(R.string.state_error_title),
        message = message,
        modifier = modifier,
    ) {
        WButton(text = stringResource(R.string.action_retry), onClick = onRetry, variant = WButtonVariant.SECONDARY)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    action: (@Composable () -> Unit)? = null,
) {
    StateMessage(icon = icon, tint = WTheme.colors.textMuted, title = title, message = message, modifier = modifier, action = action)
}

@Composable
private fun StateMessage(
    icon: ImageVector,
    tint: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(WTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(WTheme.sizes.iconXl)
                .background(tint.copy(alpha = WTheme.alpha.subtle), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(WTheme.sizes.iconLg))
        }
        Spacer(Modifier.height(WTheme.spacing.lg))
        Text(text = title, style = WTheme.typography.subtitle, color = WTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(WTheme.spacing.xs))
        Text(
            text = message,
            style = WTheme.typography.body,
            color = WTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        action?.let {
            Spacer(Modifier.height(WTheme.spacing.lg))
            it()
        }
    }
}

enum class MessageTone { ERROR, WARNING, INFO, SUCCESS }

@Composable
fun InlineMessage(message: String, tone: MessageTone, modifier: Modifier = Modifier) {
    val colors = WTheme.colors
    val (toneColor, icon) = when (tone) {
        MessageTone.ERROR -> colors.danger to Icons.Outlined.ErrorOutline
        MessageTone.WARNING -> colors.warning to Icons.Outlined.WarningAmber
        MessageTone.INFO -> colors.accent to Icons.Outlined.Info
        MessageTone.SUCCESS -> colors.success to Icons.Outlined.CheckCircle
    }
    val shape = RoundedCornerShape(WTheme.radius.md)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(toneColor.copy(alpha = WTheme.alpha.subtle), shape)
            .border(WTheme.sizes.borderThin, toneColor.copy(alpha = WTheme.alpha.border), shape)
            .padding(WTheme.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = toneColor, modifier = Modifier.size(WTheme.sizes.iconSm))
        Spacer(Modifier.width(WTheme.spacing.sm))
        Text(text = message, style = WTheme.typography.caption, color = colors.textPrimary)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    countBadge: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = WTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = WTheme.typography.label,
            color = WTheme.colors.textMuted,
        )
        countBadge?.let {
            Spacer(Modifier.width(WTheme.spacing.sm))
            CountBadge(count = it)
        }
        Spacer(Modifier.weight(1f))
        trailing?.let { Text(text = it, style = WTheme.typography.label, color = WTheme.colors.textSecondary) }
        action?.let {
            Spacer(Modifier.width(WTheme.spacing.sm))
            it()
        }
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(WTheme.colors.accent.copy(alpha = WTheme.alpha.subtle), RoundedCornerShape(WTheme.radius.sm))
            .padding(horizontal = WTheme.spacing.sm, vertical = WTheme.spacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = WTheme.typography.label,
            color = WTheme.colors.accent
        )
    }
}
