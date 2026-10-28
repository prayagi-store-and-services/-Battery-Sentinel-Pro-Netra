package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class ObservationTrendDaoTest {
    @Test fun dailyAggregationBoundsFiltersAndGroupsRealDatabase() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NetraDatabase::class.java).allowMainThreadQueries().build()
        try {
            val day = 86400000L
            fun sample(t:Long,temp:Float,level:Int=50) = BatteryRecord(timestamp=t,level=level,temperature=temp,voltageMv=4000,currentMa=0,powerWatts=0f,isCharging=false,pluggedType="BATTERY",healthStatus="UNKNOWN")
            listOf(sample(86*day,30f),sample(86*day+1000,40f),sample(99*day,38f),sample(100*day,70f),sample(85*day,20f),sample(98*day,200f),sample(97*day,30f,-1)).forEach { db.batteryDao().insert(it) }
            val rows = db.batteryDao().getDailyObservations(86*day,100*day)
            assertEquals(2,rows.size)
            assertEquals(86L,rows[0].utcDay);assertEquals(2,rows[0].sampleCount);assertEquals(35.0,rows[0].meanTemperatureC,.001)
            assertEquals(99L,rows[1].utcDay)
        } finally { db.close() }
    }
}
