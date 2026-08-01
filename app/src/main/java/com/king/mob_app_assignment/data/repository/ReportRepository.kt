package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ReportRepository {

    fun getAllReports(): Flow<List<Report>>

    fun getReportsForUser(uid: String): Flow<List<Report>>

    fun getReportById(id: String): Flow<Report?>

    suspend fun updateReportStatus(id: String, status: ReportStatus): Result<Unit>

    suspend fun findNearbyRecentReports(
        category: ReportCategory,
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        withinMinutes: Long,
    ): List<Report>

    // Checks connectivity once at call time; if offline, queues into Room PendingReportEntity
    // and returns success immediately, otherwise uploads to Storage + writes to Firestore directly.
    suspend fun submitReport(report: Report, photoFile: File?): Result<Report>

    suspend fun createReport(report: Report): Result<Report>

    // Drains the Room pending-report queue: attempts Storage upload + Firestore write for each
    // row, deleting it on success and leaving it in place (for the next retry) on failure.
    suspend fun syncPendingReports()

    fun startStatusChangeListener(uid: String)

    fun stopStatusChangeListener()
}
