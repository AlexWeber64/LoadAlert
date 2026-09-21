package com.loadalert.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.loadalert.data.model.CycleOutcome

class CycleOutcomeConverter {
    @TypeConverter
    fun toOutcome(value: String): CycleOutcome =
        CycleOutcome.entries.firstOrNull { it.name == value } ?: CycleOutcome.TIMED_OUT

    @TypeConverter
    fun fromOutcome(outcome: CycleOutcome): String = outcome.name
}

@Database(entities = [CycleRecord::class], version = 1, exportSchema = true)
@TypeConverters(CycleOutcomeConverter::class)
abstract class LoadAlertDatabase : RoomDatabase() {

    abstract fun cycleHistoryDao(): CycleHistoryDao

    companion object {
        @Volatile
        private var instance: LoadAlertDatabase? = null

        fun get(context: Context): LoadAlertDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LoadAlertDatabase::class.java,
                "loadalert.db",
            ).build().also { instance = it }
        }
    }
}
