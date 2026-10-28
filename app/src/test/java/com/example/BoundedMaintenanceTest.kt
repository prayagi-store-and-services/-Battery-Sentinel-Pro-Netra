package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.local.ActivityLog
import com.example.data.local.NetraDatabase
import com.example.data.repository.StorageCacheManager
import com.example.service.ThermalGuardianLog
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoundedMaintenanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun reset() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        context.getSharedPreferences("netra_cache_storage_prefs",0).edit().clear().commit()
        context.getSharedPreferences("netra_thermal_guardian",0).edit().clear().commit()
    }
    private fun file(name: String, bytes: Long): File = File(context.cacheDir,name).also {
        it.parentFile?.mkdirs(); RandomAccessFile(it,"rw").use { r -> r.setLength(bytes) }
    }
    @Test fun charging50MiBAndExactly200NeverAutoClean() = runBlocking {
        val f=file("one.bin",50L*1024*1024)
        StorageCacheManager(context).autoCleanIfAppropriate(true);assertTrue(f.exists())
        context.getSharedPreferences("netra_cache_storage_prefs",0).edit().clear().commit()
        RandomAccessFile(f,"rw").use { it.setLength(StorageCacheManager.MAX_CACHE_SIZE_BYTES) }
        StorageCacheManager(context).autoCleanIfAppropriate(false);assertTrue(f.exists())
    }
    @Test fun above200DeletesOnlyPayloadAndReservesAcrossRestart() = runBlocking {
        val bytes=StorageCacheManager.MAX_CACHE_SIZE_BYTES+1
        val f=file("nested/payload.bin",bytes)
        val manager=StorageCacheManager(context)
        manager.autoCleanIfAppropriate(false)
        assertFalse(f.exists());assertEquals(bytes,manager.cacheStats.value.lastFreedBytes)
        val again=file("again.bin",bytes)
        StorageCacheManager(context).autoCleanIfAppropriate(true);assertTrue(again.exists())
    }
    @Test fun explicitCleanExcludesCodeCacheProtectedFilesAndLinks() = runBlocking {
        val keep=File(context.codeCacheDir,"compiled.bin").also { it.writeText("code") }
        val db=file("keep.db",13);val xml=file("keep.xml",17)
        val outside=File(context.filesDir,"outside.bin").also { it.writeText("protected") }
        Files.createSymbolicLink(File(context.cacheDir,"outside-link").toPath(),outside.toPath())
        val f=file("nested/ok.bin",23)
        val manager=StorageCacheManager(context);assertEquals(23L,manager.calculateCacheSize().totalCacheBytes)
        assertEquals(23L,manager.cleanCache(true));assertFalse(f.exists())
        assertTrue(keep.exists());assertTrue(db.exists());assertTrue(xml.exists());assertEquals("protected",outside.readText())
    }
    @Test fun deepIncompleteScanIsUnavailableAndDoesNotDelete() = runBlocking {
        val f=file((1..34).joinToString("/"){"d"}+"/data.bin",1)
        val manager=StorageCacheManager(context)
        assertFalse(manager.calculateCacheSize().scanAvailable)
        assertEquals(0L,manager.cleanCache(true));assertTrue(f.exists())
    }
    @Test fun legacyFreedCounterIsNotShownAsVerified() = runBlocking {
        context.getSharedPreferences("netra_cache_storage_prefs",0).edit()
            .putLong("last_cleanup_timestamp",123).putLong("last_freed_bytes",4096).commit()
        val stats=StorageCacheManager(context).calculateCacheSize()
        assertEquals(0L,stats.lastCleanupTimestamp);assertEquals(0L,stats.lastFreedBytes)
    }
    @Test fun clockRollbackRebasesWithoutCleanup() = runBlocking {
        val f=file("large.bin",StorageCacheManager.MAX_CACHE_SIZE_BYTES+1)
        context.getSharedPreferences("netra_cache_storage_prefs",0).edit().putLong("last_auto_attempt_ms",Long.MAX_VALUE).commit()
        StorageCacheManager(context).autoCleanIfAppropriate(false);assertTrue(f.exists())
        StorageCacheManager(context).autoCleanIfAppropriate(false);assertTrue(f.exists())
    }
    @Test fun thermalPendingAndClosedEventsAreBounded() {
        ThermalGuardianLog.init(context);ThermalGuardianLog.beginSession(1,40f,false)
        repeat(260) { ThermalGuardianLog.addEvent("event$it"+"x".repeat(600)) }
        val p=context.getSharedPreferences("netra_thermal_guardian",0)
        val a=JSONArray(p.getString("pending_events","[]"));assertEquals(200,a.length())
        assertTrue(a.getString(199).contains("event259"));assertTrue(a.getString(0).length<=500)
        ThermalGuardianLog.endSession(60001,37f,true)
        assertEquals(200,ThermalGuardianLog.sessions.value.last().events.size)
        repeat(35) { ThermalGuardianLog.recordTest(List(250){"x".repeat(700)},38f,false) }
        assertEquals(30,ThermalGuardianLog.sessions.value.size)
        assertTrue(ThermalGuardianLog.sessions.value.all{it.events.size<=200&&it.events.all{e->e.length<=500}})
    }
    @Test fun activityLogsKeepNewest2000EvenWithClockRollback() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,NetraDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.activityLogDao()
            repeat(2005) { dao.insertBounded(ActivityLog(timestamp=10000L-it,title="event$it",message="test",category="SYSTEM")) }
            db.openHelper.readableDatabase.query("SELECT count(*), min(id), max(id) FROM activity_logs").use {
                assertTrue(it.moveToFirst());assertEquals(2000,it.getInt(0));assertEquals(6,it.getInt(1));assertEquals(2005,it.getInt(2))
            }
        } finally { db.close() }
    }
}
