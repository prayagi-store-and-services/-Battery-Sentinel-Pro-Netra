package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLog): Long

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByCategory(category: String, limit: Int = 100): Flow<List<ActivityLog>>

    /** Preserve the newest 2,000 diagnostic entries, atomically with each new entry. */
    @Transaction
    suspend fun insertBounded(log: ActivityLog): Long {
        val id = insert(log.copy(title = log.title.take(200), message = log.message.take(2000),
            category = log.category.take(80), severity = log.severity.take(40), dotColor = log.dotColor.take(40)))
        trimToRecent()
        return id
    }

    @Query("DELETE FROM activity_logs WHERE id NOT IN (SELECT id FROM activity_logs ORDER BY id DESC LIMIT 2000)")
    suspend fun trimToRecent(): Int

    @Query("DELETE FROM activity_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long): Int

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()
}
