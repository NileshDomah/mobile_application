package com.king.mob_app_assignment.domain.model

data class Report(
    val id: String = "",
    val userId: String = "",
    val category: ReportCategory = ReportCategory.OTHER,
    val photoUrl: String? = null,
    val localPhotoPath: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val status: ReportStatus = ReportStatus.SUBMITTED,
)
