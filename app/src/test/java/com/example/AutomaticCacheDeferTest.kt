package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.StorageCacheManager
import com.example.model.FieldStatus
import com.example.model.NetraCentralState
import com.example.model.TelemetryFieldState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AutomaticCacheDeferTest {
 private val context: Context = ApplicationProvider.getApplicationContext()
 private val prefs get()=context.getSharedPreferences("netra_cache_storage_prefs",0)
 @Before fun reset(){prefs.edit().clear().commit();context.cacheDir.listFiles()?.forEach{it.deleteRecursively()};context.externalCacheDir?.listFiles()?.forEach{it.deleteRecursively()}}
 private fun state(temp:Float?=30f,level:Int?=70,age:Long=0,status:FieldStatus=FieldStatus.LIVE):NetraCentralState {
  val t=System.currentTimeMillis()-age
  return NetraCentralState(batteryLevel=level,temperatureCelsius=temp,fieldStates=TelemetryFieldState(levelStatus=status,tempStatus=status,levelObservedAt=t,tempObservedAt=t))
 }
 private fun payload()=File(context.cacheDir,"automatic-defer.bin").also{RandomAccessFile(it,"rw").use{r->r.setLength(StorageCacheManager.MAX_CACHE_SIZE_BYTES+1)}}
 private suspend fun invoke(manager:StorageCacheManager,state:NetraCentralState){
  manager.autoCleanIfAppropriate(false,state)
 }
 private fun deferred(s:NetraCentralState)=runBlocking{
  val f=payload();invoke(StorageCacheManager(context),s)
  assertTrue("Deferred maintenance must not delete cache",f.exists())
  assertFalse("Deferral must not reserve six-hour budget",prefs.contains("last_auto_attempt_ms"))
 }
 @Test fun cool15BoundaryAllowsAndRetainsCooldown()=runBlocking{
  val f=payload();val manager=StorageCacheManager(context);invoke(manager,state(temp=39.99f,level=15));assertFalse(f.exists())
  val again=payload();invoke(StorageCacheManager(context),state());assertTrue(again.exists())
 }
 @Test fun missingCompatibilityContextDefers()=runBlocking{
  val f=payload();StorageCacheManager(context).autoCleanIfAppropriate(false);assertTrue(f.exists());assertFalse(prefs.contains("last_auto_attempt_ms"))
 }
 @Test fun invalidLevelDefers()=deferred(state(level=101))
 @Test fun negativeColdIsValidNotMissing()=runBlocking{
  val f=payload();invoke(StorageCacheManager(context),state(temp=-2f));assertFalse(f.exists())
 }
 @Test fun deferredThenCoolRunsWithoutWaitingSixHours()=runBlocking{
  val f=payload();val m=StorageCacheManager(context);invoke(m,state(temp=40f));assertTrue(f.exists());assertFalse(prefs.contains("last_auto_attempt_ms"))
  invoke(m,state(temp=39f));assertFalse(f.exists());assertTrue(prefs.contains("last_auto_attempt_ms"))
 }
 @Test fun individuallyStaleTemperatureDefers()=deferred(state().let{s->s.copy(fieldStates=s.fieldStates.copy(tempObservedAt=1L))})
 @Test fun individuallyStaleLevelDefers()=deferred(state().let{s->s.copy(fieldStates=s.fieldStates.copy(levelObservedAt=1L))})
 @Test fun unavailableStatusWithNumbersDefers()=deferred(state(status=FieldStatus.UNAVAILABLE))
 @Test fun infinityDefers()=deferred(state(temp=Float.POSITIVE_INFINITY))
 @Test fun negativeInfinityDefers()=deferred(state(temp=Float.NEGATIVE_INFINITY))
 @Test fun zeroTimestampDefers()=deferred(state().let{s->s.copy(fieldStates=s.fieldStates.copy(tempObservedAt=0L))})
 @Test fun fixedClockBoundaryReusesCentral120Seconds(){
  val now=1_000_000L
  val s=state().let{s->s.copy(fieldStates=s.fieldStates.copy(tempObservedAt=now-120_000L,levelObservedAt=now-120_000L))}
  assertTrue(com.example.data.repository.CacheMaintenancePolicy.contextAllowsAutomaticWork(s,now))
  assertFalse(com.example.data.repository.CacheMaintenancePolicy.contextAllowsAutomaticWork(s,now+1))
 }
 @Test fun hotAt40Defers()=deferred(state(temp=40f))
 @Test fun lowAt14Defers()=deferred(state(level=14))
 @Test fun missingTemperatureDefers()=deferred(state(temp=null))
 @Test fun nonfiniteTemperatureDefers()=deferred(state(temp=Float.NaN))
 @Test fun missingLevelDefers()=deferred(state(level=null))
 @Test fun historicalFieldDefers()=deferred(state(status=FieldStatus.LAST_VALID))
 @Test fun staleFieldsDefers()=deferred(state(age=120001))
 @Test fun futureFieldsDefers()=deferred(state(age=-5000))
}
