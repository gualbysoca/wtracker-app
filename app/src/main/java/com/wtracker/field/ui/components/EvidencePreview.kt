package com.wtracker.field.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.wtracker.field.R
import com.wtracker.field.configs.EvidenceConfig
import com.wtracker.field.services.models.CapturedEvidence
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.BitmapUtils
import com.wtracker.field.utils.GeoUtils
import com.wtracker.field.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import kotlin.math.roundToInt

private sealed interface LocalImageState {
    data object Loading : LocalImageState
    data class Loaded(val bitmap: ImageBitmap) : LocalImageState
    data object Failed : LocalImageState
}

/** Imagen local decodificada fuera del hilo principal, con estados de carga y error. */
@Composable
fun LocalEvidenceImage(filePath: String, contentDescription: String, modifier: Modifier = Modifier) {
    val imageState by produceState<LocalImageState>(LocalImageState.Loading, filePath) {
        value = withContext(Dispatchers.IO) {
            BitmapUtils.decodeSampled(File(filePath), EvidenceConfig.PREVIEW_IMAGE_EDGE_PX)
                ?.let { LocalImageState.Loaded(it.asImageBitmap()) }
                ?: LocalImageState.Failed
        }
    }

    Box(modifier = modifier.background(WTheme.colors.surfaceElevated), contentAlignment = Alignment.Center) {
        when (val state = imageState) {
            LocalImageState.Loading -> CircularProgressIndicator(
                color = WTheme.colors.accent,
                strokeWidth = WTheme.sizes.progressStroke,
                modifier = Modifier.size(WTheme.sizes.iconLg),
            )
            LocalImageState.Failed -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = WTheme.colors.textMuted,
                modifier = Modifier.size(WTheme.sizes.iconXl),
            )
            is LocalImageState.Loaded -> Image(
                bitmap = state.bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Tarjeta de evidencia adjunta: imagen sellada + metadatos de autenticidad (sólo lectura). */
@Composable
fun EvidencePreviewCard(
    evidence: CapturedEvidence,
    zone: ZoneId,
    onRetake: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WTheme.colors
    val shape = RoundedCornerShape(WTheme.radius.lg)
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surface, border = BorderStroke(WTheme.sizes.borderThin, colors.border)) {
        Column {
            LocalEvidenceImage(
                filePath = evidence.filePath,
                contentDescription = stringResource(R.string.evidence_image_description),
                modifier = Modifier.fillMaxWidth().height(WTheme.sizes.evidencePreviewHeight).clip(shape),
            )
            Column(modifier = Modifier.padding(WTheme.spacing.lg)) {
                EvidenceMetadataLines(evidence = evidence, zone = zone)
                Spacer(Modifier.height(WTheme.spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(WTheme.spacing.sm)) {
                    WButton(
                        text = stringResource(R.string.evidence_remove),
                        onClick = onRemove,
                        variant = WButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                    WButton(
                        text = stringResource(R.string.evidence_retake),
                        onClick = onRetake,
                        variant = WButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun EvidenceMetadataLines(evidence: CapturedEvidence, zone: ZoneId, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WTheme.spacing.xs)) {
        MetadataLine(
            icon = Icons.Outlined.GpsFixed,
            text = "${GeoUtils.formatCoordinate(evidence.location.coordinate)}  ±${evidence.location.accuracyMeters.roundToInt()} m",
        )
        MetadataLine(icon = Icons.Outlined.Schedule, text = TimeUtils.formatStamp(evidence.capturedAtMillis, zone))
        MetadataLine(icon = Icons.Outlined.Fingerprint, text = "SHA-256 ${evidence.sha256.take(SHA_PREVIEW_LENGTH)}…")
        MetadataLine(icon = Icons.Outlined.Verified, text = stringResource(R.string.evidence_verified_label), isHighlighted = true)
    }
}

@Composable
private fun MetadataLine(icon: ImageVector, text: String, isHighlighted: Boolean = false) {
    val tint = if (isHighlighted) WTheme.colors.success else WTheme.colors.textSecondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(WTheme.sizes.iconSm))
        Spacer(Modifier.width(WTheme.spacing.sm))
        Text(text = text, style = WTheme.typography.mono, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val SHA_PREVIEW_LENGTH = 16
