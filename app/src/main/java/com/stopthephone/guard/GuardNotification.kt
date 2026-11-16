package com.stopthephone.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.stopthephone.R
import com.stopthephone.ui.MainActivity

/**
 * 管控服务的常驻通知，用来提高进程优先级。
 */
object GuardNotification {

    private const val CHANNEL_ID = "guard"
    private const val NOTIFICATION_ID = 1

    fun startForeground(service: Service) {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "管控服务", NotificationManager.IMPORTANCE_LOW))
        val contentIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle("管控中")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, type)
    }
}
