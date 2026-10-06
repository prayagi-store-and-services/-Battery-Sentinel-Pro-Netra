package com.example.update

import android.content.Context
import java.io.File

/**
 * After an in-app update installs, Android restarts the app. On start we delete every downloaded
 * installer file from the cache folder so nothing from the update stays in storage.
 */
object UpdateFileCleanup {
    /** Deletes every file in the folder and returns how many were removed. */
    fun cleanDir(dir: File?): Int {
        var n = 0
        dir?.listFiles()?.forEach { if (it.delete()) n++ }
        return n
    }

    fun cleanLeftovers(context: Context) {
        try { cleanDir(File(context.cacheDir, "updates")) } catch (_: Exception) {}
    }

    /** Called when the app comes back to the front. Removes installer files older than one hour. */
    fun cleanStale(context: Context) {
        try {
            val now = System.currentTimeMillis()
            File(context.cacheDir, "updates").listFiles()?.forEach { if (now - it.lastModified() > 3_600_000L) it.delete() }
        } catch (_: Exception) {}
    }
}
