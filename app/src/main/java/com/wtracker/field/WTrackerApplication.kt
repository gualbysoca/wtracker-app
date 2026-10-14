package com.wtracker.field

import android.app.Application
import com.wtracker.field.configs.AppContainer
import com.wtracker.field.configs.MapConfig
import com.wtracker.field.services.location.TrackingNotificationFactory

class WTrackerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapConfig.initialize(this)
        TrackingNotificationFactory.createChannel(this)
        container.resumeBackgroundSync()
    }
}
