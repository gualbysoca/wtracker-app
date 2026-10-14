package com.wtracker.field.services.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class GpsObserver(private val context: Context) {

    fun observeIsGpsEnabled(): Flow<Boolean> = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    trySend(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                }
            }
        }

        trySend(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        
        context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))

        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
