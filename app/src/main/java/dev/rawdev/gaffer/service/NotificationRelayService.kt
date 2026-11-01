package dev.rawdev.gaffer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Optional: lets the agent see what came in while it was busy. */
class NotificationRelayService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        connected.value = true
    }

    override fun onListenerDisconnected() {
        instance = null
        connected.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val stamp = timeFormat.format(Date())
        synchronized(lock) {
            buffer.addFirst("[$stamp] ${sbn.packageName} | $title: $text")
            while (buffer.size > 60) buffer.removeLast()
        }
    }

    companion object {
        @Volatile
        var instance: NotificationRelayService? = null
            private set

        val connected = MutableStateFlow(false)

        private val buffer = ArrayDeque<String>()
        private val lock = Any()
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun recent(limit: Int): List<String> = synchronized(lock) {
            buffer.take(limit.coerceIn(1, 60))
        }
    }
}
