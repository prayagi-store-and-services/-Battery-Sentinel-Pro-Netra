package com.example.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Counts regular app cache files only, excluding code cache, links and protected file types. */
data class CacheStorageStats(
    val internalCacheBytes: Long = 0L,
    val externalCacheBytes: Long = 0L,
    val totalCacheBytes: Long = 0L,
    val isOverThreshold: Boolean = false,
    val lastCleanupTimestamp: Long = 0L,
    val lastFreedBytes: Long = 0L,
    val scanAvailable: Boolean = false
)

class StorageCacheManager(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = this.context.getSharedPreferences("netra_cache_storage_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _cacheStats = MutableStateFlow(CacheStorageStats(
        lastCleanupTimestamp = if (prefs.getBoolean("payload_bytes_verified", false)) prefs.getLong("last_cleanup_timestamp", 0L) else 0L,
        lastFreedBytes = if (prefs.getBoolean("payload_bytes_verified", false)) prefs.getLong("last_freed_bytes", 0L) else 0L))
    val cacheStats: StateFlow<CacheStorageStats> = _cacheStats.asStateFlow()

    init { refreshCacheStats() }

    fun refreshCacheStats() { scope.launch { calculateCacheSize() } }

    suspend fun calculateCacheSize(): CacheStorageStats = withContext(Dispatchers.IO) {
        mutex.withLock { calculateLocked() }
    }

    private fun roots() = listOfNotNull(context.cacheDir, context.externalCacheDir).distinctBy { it.absolutePath }

    private data class Scan(val files: List<File>, val complete: Boolean)

    private fun calculateLocked(): CacheStorageStats {
        val internal = disposableFiles(context.cacheDir)
        val external = context.externalCacheDir?.let { disposableFiles(it) } ?: Scan(emptyList(), true)
        val available = internal.complete && external.complete
        val internalBytes = internal.files.sumOf { it.length() }
        val externalBytes = external.files.sumOf { it.length() }
        return _cacheStats.value.copy(internalCacheBytes = internalBytes, externalCacheBytes = externalBytes,
            totalCacheBytes = internalBytes + externalBytes,
            isOverThreshold = available && CacheMaintenancePolicy.overThreshold(internalBytes + externalBytes),
            scanAvailable = available).also { _cacheStats.value = it }
    }

    /** force is used only by the existing explicit Clean cache button, never a telemetry caller. */
    suspend fun cleanCache(force: Boolean = false): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stats = calculateLocked()
            if (!stats.scanAvailable || (!force && !stats.isOverThreshold)) return@withLock 0L
            cleanLocked()
        }
    }

    private fun cleanLocked(): Long {
        var freed = 0L
        val scans = roots().map { disposableFiles(it) }
        if (scans.any { !it.complete }) { calculateLocked(); return 0L }
        scans.forEach { scan ->
            scan.files.forEach { file ->
                val bytes = file.length()
                if (file.delete()) freed += bytes
            }
        }
        // File payload lengths only. Directory allocation sizes are unknown and never invented.
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_cleanup_timestamp", now).putLong("last_freed_bytes", freed)
            .putBoolean("payload_bytes_verified", true).apply()
        _cacheStats.value = calculateLocked().copy(lastCleanupTimestamp = now, lastFreedBytes = freed)
        return freed
    }

    /** One app-owned scan/attempt per six hours, across restart; charging never lowers the threshold. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun autoCleanIfAppropriate(isCharging: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val last = prefs.getLong("last_auto_attempt_ms", 0L)
            // A clock rollback rebases the interval without cleaning repeatedly.
            if (last > now) { prefs.edit().putLong("last_auto_attempt_ms", now).commit(); return@withLock }
            if (!CacheMaintenancePolicy.due(now, last)) return@withLock
            // Reserve before scanning, even if nothing is removable or a scan/delete fails.
            if (!prefs.edit().putLong("last_auto_attempt_ms", now).commit()) return@withLock
            if (calculateLocked().isOverThreshold) cleanLocked()
        }
    }

    /** Bound foreground-independent traversal work. An incomplete scan is Unavailable, never zero. */
    private fun disposableFiles(root: File): Scan {
        val result = mutableListOf<File>()
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return Scan(result, false)
        var complete = true
        var visited = 0
        fun visit(dir: File, depth: Int) {
            if (!complete) return
            if (depth > 32) { complete = false; return }
            val children = dir.listFiles()
            if (children == null) { complete = false; return }
            for (file in children) {
                if (++visited > 10_000) { complete = false; return }
                val canonical = runCatching { file.canonicalFile }.getOrNull()
                if (canonical == null) { complete = false; return }
                // Refuse child links, including links pointing elsewhere within the same cache root.
                if (canonical.absolutePath != file.absoluteFile.normalize().path ||
                    !canonical.path.startsWith(canonicalRoot.path + File.separator)) continue
                if (file.name == "databases" || file.name == "shared_prefs" ||
                    file.extension.equals("db", true) || file.extension.equals("xml", true)) continue
                if (file.isDirectory) visit(file, depth + 1) else if (file.isFile) result += file
                if (!complete) return
            }
        }
        if (canonicalRoot.exists()) visit(canonicalRoot, 0)
        return Scan(result, complete)
    }

    companion object {
        const val MAX_CACHE_SIZE_BYTES = CacheMaintenancePolicy.THRESHOLD_BYTES
        const val SIX_HOURS_MS = CacheMaintenancePolicy.AUTO_INTERVAL_MS
    }
}
