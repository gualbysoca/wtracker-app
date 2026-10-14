package com.wtracker.field

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wtracker.field.routes.AppNavGraph
import com.wtracker.field.ui.theme.WTrackerTheme

/** Única Activity: hospeda Compose y delega toda la navegación en [AppNavGraph]. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WTrackerApplication).container
        setContent {
            val isLightMode by container.appPreferencesStore.isLightModeFlow.collectAsStateWithLifecycle(initialValue = false)
            WTrackerTheme(isLightMode = isLightMode) {
                AppNavGraph(container = container)
            }
        }
    }
}
