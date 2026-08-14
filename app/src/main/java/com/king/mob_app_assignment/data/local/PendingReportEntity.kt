package com.king.mob_app_assignment.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val category: String,
    val customLabel: String = "",
    val localPhotoPath: String,
    // Populated instead of localPhotoPath when mediaType == "VIDEO" - mirrors Report's
    // mediaType/localVideoPath split. localPhotoPath stays "" for a queued video report.
    val mediaType: String = "PHOTO",
    val localVideoPath: String = "",
    val isAnonymous: Boolean = false,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val headingDegrees: Float,
    val headingCardinal: String,
    val timestamp: Long,
    val note: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
)
