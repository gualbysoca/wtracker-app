package com.wtracker.field.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wtracker.field.R

/** Informa al trabajador sobre el estado de sus sensores (Internet y GPS) y registros pendientes. */
@Composable
fun DiagnosticBanner(isOnline: Boolean, isGpsEnabled: Boolean, pendingSyncCount: Int, modifier: Modifier = Modifier) {
    val isVisible = !isOnline || !isGpsEnabled || pendingSyncCount > 0
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val diagnosticIssues = mutableListOf<String>()
        if (!isGpsEnabled) diagnosticIssues.add("GPS apagado")
        if (!isOnline) diagnosticIssues.add("Sin conexión")
        
        val message = when {
            diagnosticIssues.isNotEmpty() && pendingSyncCount > 0 ->
                "${diagnosticIssues.joinToString(" • ")}. Pendientes: $pendingSyncCount"
            diagnosticIssues.isNotEmpty() -> 
                diagnosticIssues.joinToString(" • ")
            else -> pluralStringResource(R.plurals.banner_syncing, pendingSyncCount, pendingSyncCount)
        }
        InlineMessage(message = message, tone = if (diagnosticIssues.isEmpty()) MessageTone.INFO else MessageTone.WARNING)
    }
}
