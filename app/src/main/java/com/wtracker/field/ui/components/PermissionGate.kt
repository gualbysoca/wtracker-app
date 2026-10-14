package com.wtracker.field.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wtracker.field.R
import com.wtracker.field.ui.theme.WTheme
import com.wtracker.field.utils.findActivity
import com.wtracker.field.utils.hasPermissions
import com.wtracker.field.utils.openAppSettings

/**
 * Bloquea el contenido hasta tener los permisos obligatorios. Solicita automáticamente al
 * entrar, re-evalúa al volver de Ajustes y ofrece ir a Ajustes si fueron denegados permanentemente.
 */
@Composable
fun PermissionGate(
    requestedPermissions: List<String>,
    requiredPermissions: List<String>,
    rationaleTitle: String,
    rationaleMessage: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Lock,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isGranted by remember { mutableStateOf(context.hasPermissions(requiredPermissions)) }
    var hasRequestedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasRequestedOnce = true
        isGranted = context.hasPermissions(requiredPermissions)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isGranted = context.hasPermissions(requiredPermissions)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!isGranted && !hasRequestedOnce) permissionLauncher.launch(requestedPermissions.toTypedArray())
    }

    if (isGranted) {
        content()
        return
    }

    val activity = context.findActivity()
    val isPermanentlyDenied = hasRequestedOnce && activity != null &&
        requiredPermissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    Box(
        modifier = modifier.fillMaxSize().background(WTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = rationaleTitle,
            message = rationaleMessage,
            icon = icon,
            action = {
                WButton(
                    text = stringResource(if (isPermanentlyDenied) R.string.permission_open_settings else R.string.permission_grant),
                    onClick = {
                        if (isPermanentlyDenied) context.openAppSettings()
                        else permissionLauncher.launch(requestedPermissions.toTypedArray())
                    },
                )
            },
        )
    }
}
