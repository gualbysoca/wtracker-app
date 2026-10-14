package com.wtracker.field.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.controllers.UpdateUiState
import com.wtracker.field.ui.theme.WTheme

/** Aviso de nueva versión con el botón para descargarla e instalarla. No muestra nada si no hay actualización. */
@Composable
fun UpdateBanner(state: UpdateUiState, onUpdateClick: () -> Unit, modifier: Modifier = Modifier) {
    val release = state.availableRelease ?: return
    val colors = WTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WTheme.radius.lg),
        color = colors.surface,
        border = BorderStroke(WTheme.sizes.borderThin, colors.accent),
    ) {
        Column(modifier = Modifier.padding(WTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(WTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(WTheme.sizes.iconXl)
                        .background(colors.accent.copy(alpha = WTheme.alpha.subtle), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(WTheme.sizes.iconLg),
                    )
                }
                Spacer(Modifier.width(WTheme.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_available_title),
                        style = WTheme.typography.subtitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.update_version_label, release.versionName),
                        style = WTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            release.notes?.let { notes ->
                Text(
                    text = notes,
                    style = WTheme.typography.body,
                    color = colors.textSecondary,
                    maxLines = NOTES_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.isDownloading) {
                LinearProgressIndicator(
                    progress = { state.downloadProgressPercent / PERCENT_SCALE },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.surfaceElevated,
                )
            }
            state.errorMessage?.let { InlineMessage(message = it, tone = MessageTone.ERROR) }
            WButton(
                text = if (state.isDownloading) {
                    stringResource(R.string.update_downloading, state.downloadProgressPercent)
                } else {
                    stringResource(R.string.update_action)
                },
                onClick = onUpdateClick,
                enabled = !state.isDownloading,
                leadingIcon = Icons.Outlined.SystemUpdate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val NOTES_MAX_LINES = 4
private const val PERCENT_SCALE = 100f
