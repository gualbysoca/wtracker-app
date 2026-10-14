package com.wtracker.field.ui.components

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wtracker.field.R
import com.wtracker.field.configs.LocationConfig
import com.wtracker.field.services.models.CapturedLocation
import com.wtracker.field.ui.theme.WTheme
import kotlin.math.roundToInt

/** Superficie de vista previa de CameraX. No ofrece controles de edición ni de galería. */
@Composable
fun CameraViewport(
    onSurfaceReady: (LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE (TextureView) permite superponer UI de Compose sin artefactos.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    LaunchedEffect(previewView, lifecycleOwner) { onSurfaceReady(lifecycleOwner, previewView.surfaceProvider) }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun ShutterButton(onClick: () -> Unit, isCapturing: Boolean, modifier: Modifier = Modifier) {
    val colors = WTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val innerScale by animateFloatAsState(
        targetValue = if (isPressed || isCapturing) SHUTTER_PRESSED_SCALE else 1f,
        label = "shutterScale",
    )
    val shutterDescription = stringResource(R.string.camera_shutter_description)

    Box(
        modifier = modifier
            .size(WTheme.sizes.shutterOuter)
            .border(WTheme.sizes.borderThick * 2, colors.cameraContent, CircleShape)
            .selectable(
                selected = false,
                enabled = !isCapturing,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = shutterDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(WTheme.sizes.shutterInner)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .background(Brush.linearGradient(listOf(colors.brandGradientStart, colors.brandGradientEnd)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    color = colors.cameraContent,
                    strokeWidth = WTheme.sizes.progressStroke,
                    modifier = Modifier.size(WTheme.sizes.iconLg),
                )
            }
        }
    }
}

/** Indicador en vivo de la calidad del GPS mientras se apunta la cámara. */
@Composable
fun GpsAccuracyPill(location: CapturedLocation?, modifier: Modifier = Modifier) {
    val colors = WTheme.colors
    val (tone, icon, label) = when {
        location == null -> Triple(colors.textMuted, Icons.Outlined.GpsNotFixed, stringResource(R.string.camera_gps_searching))
        location.isMockLocation -> Triple(colors.danger, Icons.Outlined.GpsOff, stringResource(R.string.camera_gps_mock))
        location.accuracyMeters <= LocationConfig.GOOD_ACCURACY_METERS ->
            Triple(colors.success, Icons.Outlined.GpsFixed, stringResource(R.string.camera_gps_accuracy, location.accuracyMeters.roundToInt()))
        location.accuracyMeters <= LocationConfig.MAX_ACCEPTABLE_ACCURACY_METERS ->
            Triple(colors.warning, Icons.Outlined.GpsFixed, stringResource(R.string.camera_gps_accuracy, location.accuracyMeters.roundToInt()))
        else -> Triple(colors.danger, Icons.Outlined.GpsNotFixed, stringResource(R.string.camera_gps_weak, location.accuracyMeters.roundToInt()))
    }

    Row(
        modifier = modifier
            .background(colors.cameraOverlay, CircleShape)
            .border(WTheme.sizes.borderThin, tone.copy(alpha = WTheme.alpha.border), CircleShape)
            .padding(horizontal = WTheme.spacing.md, vertical = WTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(WTheme.sizes.iconSm))
        Spacer(Modifier.width(WTheme.spacing.xs))
        Text(text = label, style = WTheme.typography.label, color = colors.cameraContent)
    }
}

private const val SHUTTER_PRESSED_SCALE = 0.86f
