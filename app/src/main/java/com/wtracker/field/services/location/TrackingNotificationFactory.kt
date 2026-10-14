package com.wtracker.field.services.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.wtracker.field.R
import com.wtracker.field.configs.TrackingNotificationConfig

/** Construye la notificación persistente obligatoria del servicio de jornada. */
object TrackingNotificationFactory {

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            TrackingNotificationConfig.CHANNEL_ID,
            context.getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.tracking_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val highPriorityChannel = NotificationChannel(
            TrackingNotificationConfig.HIGH_PRIORITY_CHANNEL_ID,
            "Alertas de Jornada",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alertas urgentes de abandono de jornada"
            setShowBadge(true)
        }
        manager.createNotificationChannel(highPriorityChannel)
    }

    fun build(context: Context, startedAtMillis: Long?): Notification {
        val builder = NotificationCompat.Builder(context, TrackingNotificationConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(buildOpenAppIntent(context))

        startedAtMillis?.let { builder.setWhen(it).setUsesChronometer(true).setShowWhen(true) }
        return builder.build()
    }

    fun buildAbandonmentNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, TrackingNotificationConfig.HIGH_PRIORITY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle("Turno pendiente detectado")
            .setContentText("Tu turno parece inactivo. ¿Sigues trabajando?")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_stat_tracking,
                "Sí, sigo trabajando",
                buildCancelAbandonmentIntent(context)
            )
            .setContentIntent(buildOpenAppIntent(context))
            .build()
    }

    private fun buildCancelAbandonmentIntent(context: Context): PendingIntent {
        val intent = android.content.Intent(context, ShiftTrackingService::class.java).apply {
            action = ShiftTrackingService.ACTION_CANCEL_AUTO_CHECKOUT
        }
        return PendingIntent.getService(
            context,
            TrackingNotificationConfig.ABANDONMENT_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            TrackingNotificationConfig.NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
