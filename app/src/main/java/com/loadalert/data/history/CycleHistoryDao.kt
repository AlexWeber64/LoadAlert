package com.loadalert.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleHistoryDao {

    @Query("SELECT * FROM cycle_history ORDER BY startedAtEpochMs DESC")
    fun observeAll(): Flow<List<CycleRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CycleRecord)

    @Query("DELETE FROM cycle_history")
    suspend fun clear()
}
