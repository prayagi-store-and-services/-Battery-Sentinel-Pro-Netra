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

    /** A saved installer for a version newer than the installed one is kept (it was saved in the background); anything else is removed. */
    fun keep(fileName: String, installedCode: Long, ageMs: Long): Boolean {
        val code = Regex("battery-sentinel-pro-netra-([0-9]+)\\.apk").matchEntire(fileName)?.groupValues?.get(1)?.toLongOrNull()
            ?: return ageMs <= 3_600_000L
        return code > installedCode
    }

    fun cleanLeftovers(context: Context) {
        try {
            val installed = com.example.BuildConfig.VERSION_CODE.toLong()
            val now = System.currentTimeMillis()
            File(context.cacheDir, "updates").listFiles()?.forEach { if (!keep(it.name, installed, now - it.lastModified())) it.delete() }
        } catch (_: Exception) {}
    }

    /** Called when the app comes back to the front. Removes installer files older than one hour. */
    fun cleanStale(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val installed = com.example.BuildConfig.VERSION_CODE.toLong()
            File(context.cacheDir, "updates").listFiles()?.forEach { if (!keep(it.name, installed, now - it.lastModified())) it.delete() }
        } catch (_: Exception) {}
    }
}
