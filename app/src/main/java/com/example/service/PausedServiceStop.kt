package com.example.service

import android.app.Service
import android.os.Build
import android.util.Log

/** Fulfill an O+ foreground start promptly even when the crash gate will immediately stop it. */
internal object PausedServiceStop {
    internal fun inOrder(promote: () -> Unit, stop: () -> Unit) {
        try {
            promote()
        } catch (failure: Throwable) {
            Log.e("PausedServiceStop", "Paused foreground promotion refused; stopping service", failure)
        } finally {
            stop()
        }
    }
    fun stop(service: Service) {
        inOrder(
            promote = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    SentinelStatusNotification.ensureChannel(service)
                    service.startForeground(SentinelStatusNotification.NOTIFICATION_ID,
                        SentinelStatusNotification.buildPaused(service))
                }
            },
            stop = { service.stopSelf() }
        )
    }
}
