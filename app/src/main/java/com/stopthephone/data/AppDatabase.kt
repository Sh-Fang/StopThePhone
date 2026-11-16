package com.stopthephone.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ControlledApp::class, ControlPeriod::class, Release::class, InterceptEvent::class, UsageSession::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun controlDao(): ControlDao

    abstract fun recordDao(): RecordDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "stopthephone.db").build()
    }
}
