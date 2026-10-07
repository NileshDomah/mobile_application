package com.king.mob_app_assignment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PendingReportEntity::class],
    // Bumped again for PendingReportEntity.reporterName (admin-visible reporter name) - same
    // fallbackToDestructiveMigration() reasoning as the version 2 bump above applies here too.
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingReportDao(): PendingReportDao
}
