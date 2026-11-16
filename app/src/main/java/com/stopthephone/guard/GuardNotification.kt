package com.stopthephone.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.stopthephone.R
import com.stopthephone.ui.MainActivity

/**
 * 管控服务的常驻通知，用来提高进程优先级，顺带显示当前状态和今日次数。
 */
object GuardNotification {

    private const val CHANNEL_ID = "guard"
    private const val NOTIFICATION_ID = 1

    fun startForeground(service: Service, title: String, text: String) {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "管控服务", NotificationManager.IMPORTANCE_LOW))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(service, NOTIFICATION_ID, build(service, title, text), type)
    }

    fun stop(service: Service) {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    fun update(context: Context, title: String, text: String) {
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context, title, text))
    }

    private fun build(context: Context, title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()
    }
}
