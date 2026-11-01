package dev.rawdev.gaffer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dev.rawdev.gaffer.MainActivity

/**
 * A low-priority foreground notification that keeps the process alive while
 * the agent is driving other apps. Android is much less likely to kill a
 * foreground service than a background one.
 */
class AgentKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Agent running", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Gaffer is working")
            .setContentText("Tap to watch what it is doing.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(open)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    companion object {
        private const val CHANNEL = "gaffer_agent"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            try {
                val i = Intent(context, AgentKeepAliveService::class.java)
                context.startForegroundService(i)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AgentKeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
