package com.behaviormonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room 数据库
 */
@Database(
    entities = [StateEventEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(MonitorStateConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stateEventDao(): StateEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "behavior_monitor.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
