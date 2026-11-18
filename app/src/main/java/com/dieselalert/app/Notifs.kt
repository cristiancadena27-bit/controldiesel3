package com.dieselalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifs {
    const val CH_TRIP = "trip"
    const val CH_INFO = "info"
    const val CH_ALERT = "alert"

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRIP, "Recorrido en curso", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Resumen de recorrido", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alerta de diésel", NotificationManager.IMPORTANCE_HIGH))
    }

    fun build(
        c: Context, ch: String, title: String, text: String,
        ongoing: Boolean = false, actions: List<NotificationCompat.Action> = emptyList()
    ): Notification {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(c, ch)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF0B5CAD.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
        actions.forEach { b.addAction(it) }
        return b.build()
    }

    fun post(
        c: Context, id: Int, title: String, text: String, ch: String,
        ongoing: Boolean = false, actions: List<NotificationCompat.Action> = emptyList()
    ) {
        try {
            val nm = NotificationManagerCompat.from(c)
            if (nm.areNotificationsEnabled()) nm.notify(id, build(c, ch, title, text, ongoing, actions))
        } catch (_: SecurityException) {
        }
    }
}
