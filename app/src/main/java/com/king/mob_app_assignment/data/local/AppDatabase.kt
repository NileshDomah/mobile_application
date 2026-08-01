package com.king.mob_app_assignment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PendingReportEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingReportDao(): PendingReportDao
}
