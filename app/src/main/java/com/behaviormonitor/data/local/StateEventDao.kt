package com.behaviormonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 状态事件 DAO
 */
@Dao
interface StateEventDao {

    @Insert
    suspend fun insert(event: StateEventEntity): Long

    @Query("SELECT * FROM state_events WHERE date(timestamp / 1000, 'unixepoch') = :date ORDER BY timestamp ASC")
    fun queryByDate(date: String): Flow<List<StateEventEntity>>

    @Query("SELECT * FROM state_events ORDER BY timestamp ASC")
    fun queryAll(): Flow<List<StateEventEntity>>

    @Query("DELETE FROM state_events")
    suspend fun deleteAll()
}
