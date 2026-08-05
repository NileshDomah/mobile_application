package com.king.mob_app_assignment.domain.model

data class ImpactStats(
    val totalReports: Int = 0,
    val resolvedReports: Int = 0,
)

fun List<Report>.toImpactStats(): ImpactStats = ImpactStats(
    totalReports = size,
    resolvedReports = count { it.status == ReportStatus.RESOLVED },
)
