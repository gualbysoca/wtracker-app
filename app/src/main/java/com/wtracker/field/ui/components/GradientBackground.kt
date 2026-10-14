package com.wtracker.field.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.wtracker.field.ui.theme.WTheme

/** Fondo de marca con degradado vertical, compartido por pantallas de entrada (splash, login). */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = WTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.backgroundGradientTop, colors.background, colors.backgroundGradientBottom))),
        contentAlignment = contentAlignment,
        content = content,
    )
}
