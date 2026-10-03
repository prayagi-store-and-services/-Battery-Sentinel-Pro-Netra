package com.example.service

import android.service.notification.NotificationListenerService

/**
 * Lets the Saver clear notifications. Needs "Notification access", which the user grants once in
 * Android settings. It only calls cancelAllNotifications(), which Android limits to clearable ones:
 * ongoing items such as calls, music players and foreground services stay. It never reads or stores
 * notification content.
 */
class SaverNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    companion object {
        @Volatile private var instance: SaverNotificationListener? = null
        fun isConnected(): Boolean = instance != null
        fun clearAll(): Boolean = try {
            instance?.cancelAllNotifications()
            instance != null
        } catch (e: Exception) { false }
    }
}
