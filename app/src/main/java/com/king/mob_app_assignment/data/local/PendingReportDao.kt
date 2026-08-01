package com.king.mob_app_assignment.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingReportDao {

    @Insert
    suspend fun insert(pendingReport: PendingReportEntity): Long

    @Query("SELECT * FROM pending_reports ORDER BY createdAt ASC")
    fun getAll(): Flow<List<PendingReportEntity>>

    @Query("SELECT * FROM pending_reports ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PendingReportEntity>

    @Delete
    suspend fun delete(pendingReport: PendingReportEntity)

    @Query("DELETE FROM pending_reports WHERE id = :id")
    suspend fun deleteById(id: Long)
}
