package com.wtracker.field.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta cruda. NUNCA usar directamente en componentes: usar [WColorScheme] (semántico). */
internal object WPalette {
    val Ink950 = Color(0xFF070B16)
    val Ink900 = Color(0xFF0A0F1E)
    val Ink800 = Color(0xFF121A2E)
    val Ink700 = Color(0xFF1A2440)
    val Ink600 = Color(0xFF26324F)
    val Mist100 = Color(0xFFF2F5FB)
    val Mist300 = Color(0xFFA9B4CC)
    val Mist500 = Color(0xFF6B7794)
    val Indigo400 = Color(0xFF5B8CFF)
    val Violet400 = Color(0xFF8B5CF6)
    val Cyan400 = Color(0xFF22D3EE)
    val Amber400 = Color(0xFFF5B544)
    val Emerald400 = Color(0xFF34D399)
    val Coral400 = Color(0xFFF87171)
    val Slate400 = Color(0xFF8A94AD)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    // Extra high-contrast colors for light mode
    val Blue700 = Color(0xFF1D4ED8)
    val Amber600 = Color(0xFFD97706)
    val Emerald600 = Color(0xFF059669)
    val Coral600 = Color(0xFFDC2626)
    val Slate600 = Color(0xFF475569)
}

@Immutable
data class WColorScheme(
    val background: Color,
    val backgroundGradientTop: Color,
    val backgroundGradientBottom: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentContent: Color,
    val brandGradientStart: Color,
    val brandGradientEnd: Color,
    val statusPending: Color,
    val statusEnRoute: Color,
    val statusVisited: Color,
    val statusFailed: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val cameraBackground: Color,
    val cameraOverlay: Color,
    val cameraContent: Color,
)

internal val DarkWColorScheme = WColorScheme(
    background = WPalette.Ink900,
    backgroundGradientTop = WPalette.Ink700,
    backgroundGradientBottom = WPalette.Ink950,
    surface = WPalette.Ink800,
    surfaceElevated = WPalette.Ink700,
    border = WPalette.Ink600,
    textPrimary = WPalette.Mist100,
    textSecondary = WPalette.Mist300,
    textMuted = WPalette.Mist500,
    accent = WPalette.Indigo400,
    accentContent = WPalette.White,
    brandGradientStart = WPalette.Indigo400,
    brandGradientEnd = WPalette.Cyan400,
    statusPending = WPalette.Slate400,
    statusEnRoute = WPalette.Amber400,
    statusVisited = WPalette.Emerald400,
    statusFailed = WPalette.Coral400,
    success = WPalette.Emerald400,
    warning = WPalette.Amber400,
    danger = WPalette.Coral400,
    cameraBackground = WPalette.Black,
    cameraOverlay = WPalette.Ink950.copy(alpha = 0.64f),
    cameraContent = WPalette.White,
)

internal val LightWColorScheme = WColorScheme(
    background = WPalette.White,
    backgroundGradientTop = WPalette.Mist100,
    backgroundGradientBottom = WPalette.White,
    surface = WPalette.White,
    surfaceElevated = WPalette.Mist100,
    border = WPalette.Mist300,
    textPrimary = WPalette.Ink950,
    textSecondary = WPalette.Ink700,
    textMuted = WPalette.Mist500,
    accent = WPalette.Blue700,
    accentContent = WPalette.White,
    brandGradientStart = WPalette.Blue700,
    brandGradientEnd = WPalette.Violet400,
    statusPending = WPalette.Slate600,
    statusEnRoute = WPalette.Amber600,
    statusVisited = WPalette.Emerald600,
    statusFailed = WPalette.Coral600,
    success = WPalette.Emerald600,
    warning = WPalette.Amber600,
    danger = WPalette.Coral600,
    cameraBackground = WPalette.White,
    cameraOverlay = WPalette.White.copy(alpha = 0.92f),
    cameraContent = WPalette.Ink950,
)

@Immutable
data class WSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

@Immutable
data class WRadius(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
)

@Immutable
data class WSizes(
    val touchTarget: Dp = 52.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 24.dp,
    val iconLg: Dp = 32.dp,
    val iconXl: Dp = 56.dp,
    val orderBadge: Dp = 36.dp,
    val statusDot: Dp = 8.dp,
    val borderThin: Dp = 1.dp,
    val borderThick: Dp = 2.dp,
    val progressStroke: Dp = 2.dp,
    val progressTrack: Dp = 6.dp,
    val brandMark: Dp = 88.dp,
    val shutterOuter: Dp = 84.dp,
    val shutterInner: Dp = 68.dp,
    val mapHeight: Dp = 240.dp,
    val evidencePreviewHeight: Dp = 280.dp,
    val maxContentWidth: Dp = 480.dp,
    val metricTileMinHeight: Dp = 104.dp,
)

@Immutable
data class WAlpha(
    val disabled: Float = 0.38f,
    val subtle: Float = 0.14f,
    val border: Float = 0.4f,
    val pressedScale: Float = 0.97f,
)

@Immutable
data class WMotion(
    val shortMillis: Int = 150,
    val mediumMillis: Int = 250,
    val longMillis: Int = 400,
)

@Immutable
data class WTypography(
    val display: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val mono: TextStyle,
    val metric: TextStyle,
)

private val BaseFontFamily = FontFamily.SansSerif

internal val DefaultWTypography = WTypography(
    display = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    title = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    subtitle = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyStrong = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    label = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    button = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    metric = TextStyle(fontFamily = BaseFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp),
)
