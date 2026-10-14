package com.wtracker.field.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.wtracker.field.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.wtracker.field.ui.theme.WTheme

/** Marca de la app con gradiente y un "respiro" sutil (micro-animación). */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val colors = WTheme.colors
    val breathing = rememberInfiniteTransition(label = "brandBreathing")
    val scale by breathing.animateFloat(
        initialValue = 1f,
        targetValue = BREATH_SCALE,
        animationSpec = infiniteRepeatable(tween(WTheme.motion.longMillis * BREATH_DURATION_MULTIPLIER), RepeatMode.Reverse),
        label = "brandScale",
    )

    Box(
        modifier = modifier
            .size(WTheme.sizes.brandMark)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                brush = Brush.linearGradient(listOf(colors.brandGradientStart, colors.brandGradientEnd)),
                shape = RoundedCornerShape(WTheme.radius.xl),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_wtracker_logo),
            contentDescription = null,
            tint = colors.accentContent,
            modifier = Modifier.size(WTheme.sizes.iconXl),
        )
    }
}

private const val BREATH_SCALE = 1.04f
private const val BREATH_DURATION_MULTIPLIER = 4
