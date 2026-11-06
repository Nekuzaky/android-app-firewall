package dev.nekuz.appwall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notices {
    const val ONGOING_ID = 1
    private const val OFF_ID = 2
    private const val CHANNEL_STATUS = "status"
    private const val CHANNEL_ALERTS = "alerts"

    fun ongoing(context: Context): Notification {
        createChannels(context)
        return Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(context.getString(R.string.notification_on_title))
            .setContentText(context.getString(R.string.notification_on_text))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()
    }

    fun showOff(context: Context) {
        runCatching {
            createChannels(context)
            val notification = Notification.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(context.getString(R.string.notification_off_title))
                .setContentText(context.getString(R.string.notification_off_text))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.notification_off_text)))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(OFF_ID, notification)
        }
    }

    fun clearOff(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(OFF_ID) }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        )
    }
}
