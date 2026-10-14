package com.wtracker.field.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.ui.theme.WTheme

@Composable
fun WTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = WTheme.colors
    val isLight = colors.background == Color.White || colors.background == Color(0xFFFFFFFF)
    
    val backgroundModifier = if (isLight) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF6B7794), Color(0xFFE2E8F0)) // Plomo oscuro a plomo claro
            )
        )
    } else {
        Modifier.background(colors.background)
    }

    androidx.compose.foundation.layout.Box(modifier = backgroundModifier) {
        TopAppBar(
            modifier = modifier,
            title = {
                Column {
                    Text(text = title, style = WTheme.typography.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(text = it, style = WTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            navigationIcon = {
                onBack?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = colors.textPrimary,
                        )
                    }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = colors.textPrimary,
                actionIconContentColor = colors.textSecondary,
            ),
        )
    }
}
