package com.wtracker.field.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalWColors = staticCompositionLocalOf { DarkWColorScheme }
private val LocalWSpacing = staticCompositionLocalOf { WSpacing() }
private val LocalWRadius = staticCompositionLocalOf { WRadius() }
private val LocalWSizes = staticCompositionLocalOf { WSizes() }
private val LocalWAlpha = staticCompositionLocalOf { WAlpha() }
private val LocalWMotion = staticCompositionLocalOf { WMotion() }
private val LocalWTypography = staticCompositionLocalOf { DefaultWTypography }

/** Punto de acceso único a los tokens: `WTheme.colors.accent`, `WTheme.spacing.lg`, etc. */
object WTheme {
    val colors: WColorScheme
        @Composable @ReadOnlyComposable get() = LocalWColors.current
    val spacing: WSpacing
        @Composable @ReadOnlyComposable get() = LocalWSpacing.current
    val radius: WRadius
        @Composable @ReadOnlyComposable get() = LocalWRadius.current
    val sizes: WSizes
        @Composable @ReadOnlyComposable get() = LocalWSizes.current
    val alpha: WAlpha
        @Composable @ReadOnlyComposable get() = LocalWAlpha.current
    val motion: WMotion
        @Composable @ReadOnlyComposable get() = LocalWMotion.current
    val typography: WTypography
        @Composable @ReadOnlyComposable get() = LocalWTypography.current
}

@Composable
fun WTrackerTheme(isLightMode: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (isLightMode) LightWColorScheme else DarkWColorScheme
    // Material3 se alinea a los tokens para que sus componentes base hereden el mismo "vibe".
    val materialColors = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.accentContent,
        secondary = colors.brandGradientEnd,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceElevated,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        error = colors.danger,
    )

    val materialColorsTheme = if (isLightMode) {
        androidx.compose.material3.lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentContent,
            secondary = colors.brandGradientEnd,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.danger,
        )
    } else {
        materialColors
    }

    CompositionLocalProvider(
        LocalWColors provides colors,
        LocalWSpacing provides WSpacing(),
        LocalWRadius provides WRadius(),
        LocalWSizes provides WSizes(),
        LocalWAlpha provides WAlpha(),
        LocalWMotion provides WMotion(),
        LocalWTypography provides DefaultWTypography,
    ) {
        MaterialTheme(colorScheme = materialColorsTheme, content = content)
    }
}
