package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.NetraApplication
import com.example.service.BatteryMonitorService

/** Component-explicit notification actions, independent of service receiver lifetime. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BatteryMonitorService.ACTION_DISMISS_ALARM && intent.action != BatteryMonitorService.ACTION_SET_TARGET_100) return
        val session = intent.getLongExtra(EXTRA_SESSION, 0L)
        if (session == 0L) return
        val live = try { NetraApplication.instance.centralDataCenter.centralState.value } catch (_: Exception) { null }
        if (live?.isDataFresh == true) {
            if (live.isChargerConnected == false && live.isCharging != true) return
            if (live.isChargerConnected == true && live.chargerConnectedAt != null && live.chargerConnectedAt != session) return
        }
        val knownActive = com.example.service.TargetAlarmRepeat.sessionIdentity == session
        val verifiedActive = live?.isChargerConnected == true && live.chargerConnectedAt == session
        if (!knownActive && !verifiedActive) return
        when (intent.action) {
            BatteryMonitorService.ACTION_DISMISS_ALARM -> {
                com.example.service.TargetAlarmRepeat.muted = true
                com.example.service.AlertDeliveryState(context).action(session, mute = true)
            }
            BatteryMonitorService.ACTION_SET_TARGET_100 -> {
                com.example.service.TargetAlarmRepeat.sessionTarget = 100
                com.example.service.AlertDeliveryState(context).action(session, override = 100)
            }
            else -> return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(BatteryMonitorService.NOTIFICATION_ALARM_ID)
    }

    companion object {
        private const val EXTRA_SESSION = "alert_session"
        fun intent(context: Context, action: String, session: Long? = com.example.service.TargetAlarmRepeat.sessionIdentity): Intent =
            Intent(context, AlarmActionReceiver::class.java).setAction(action).setPackage(context.packageName)
                .putExtra(EXTRA_SESSION, session ?: 0L)
                .setData(android.net.Uri.parse("netra-alarm://session/${session ?: 0L}"))
    }
}
