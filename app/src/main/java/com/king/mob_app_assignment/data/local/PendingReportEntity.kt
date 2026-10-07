package com.king.mob_app_assignment.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    // See Report.reporterName's own doc comment - denormalized reporter display name, carried
    // through the offline queue the same way every other report field already is so a queued
    // report still has it once syncPendingReports() eventually uploads it.
    val reporterName: String = "",
    val category: String,
    val customLabel: String = "",
    val localPhotoPath: String,
    // Every photo path after the first (localPhotoPath is always the primary one), joined with
    // ADDITIONAL_PHOTO_PATHS_DELIMITER rather than a Room TypeConverter/child table - file paths
    // never contain this delimiter, so a plain String column round-trips cleanly with a
    // split()/joinToString() pair (see PendingReportEntity.additionalLocalPhotoPathsList and
    // ReportRepositoryImpl's use of it) without the extra schema machinery a real list column
    // would need. Empty string means no additional photos, same as an empty list.
    val additionalLocalPhotoPaths: String = "",
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
) {
    companion object {
        const val ADDITIONAL_PHOTO_PATHS_DELIMITER = "|"
    }
}

/** Splits [PendingReportEntity.additionalLocalPhotoPaths] back into a list - see that field's doc. */
fun PendingReportEntity.additionalLocalPhotoPathsList(): List<String> =
    additionalLocalPhotoPaths.takeIf { it.isNotBlank() }
        ?.split(PendingReportEntity.ADDITIONAL_PHOTO_PATHS_DELIMITER)
        ?.filter { it.isNotBlank() }
        ?: emptyList()
