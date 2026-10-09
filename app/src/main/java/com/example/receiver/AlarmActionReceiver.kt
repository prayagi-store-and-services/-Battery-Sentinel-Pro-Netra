package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NetraApplication
import com.example.data.repository.SettingsRepository
import com.example.service.BatteryMonitorService

/** Component-explicit notification actions, independent of service receiver lifetime. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BatteryMonitorService.ACTION_DISMISS_ALARM -> com.example.service.TargetAlarmRepeat.muted = true
            BatteryMonitorService.ACTION_SET_TARGET_100 -> {
                val app = context.applicationContext
                val settings = if (app is NetraApplication) app.settingsRepository else SettingsRepository(context)
                settings.updateChargeTarget(100)
            }
            else -> return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(BatteryMonitorService.NOTIFICATION_ALARM_ID)
    }

    companion object {
        fun intent(context: Context, action: String): Intent =
            Intent(context, AlarmActionReceiver::class.java).setAction(action).setPackage(context.packageName)
    }
}
