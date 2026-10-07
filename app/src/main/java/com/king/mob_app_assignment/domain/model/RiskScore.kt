package com.king.mob_app_assignment.domain.model

/**
 * A transparent, rule-based 0-100 risk score for one (area, category) pair - "Predictive
 * Infrastructure Intelligence" per the brief. Deliberately NOT presented as a trained ML model:
 * every contributing RiskFactor is stored and shown, same philosophy as PriorityScorer/Incident's
 * priorityFactors. The architecture (a pure function over historical counts, in
 * RiskScorer.kt) is written so a real ML model could later replace RiskScorer.score() without
 * changing any caller - RiskScoreRepository/the admin UI only ever depend on the RiskScore shape
 * below, never on how it was computed.
 *
 * Areas are not a pre-existing entity in this project (no formal ward/zone/district data) - see
 * areaKeyFor() in RiskScorer.kt for how a report's lat/lng is bucketed into a coarse grid cell
 * used as the area identity. This is intentionally coarse (documented limitation, see
 * PLACEHOLDERS_TODO.csv) rather than pretending at a precision the underlying data doesn't
 * support.
 */
enum class RiskLevel {
    LOW, MODERATE, HIGH, CRITICAL;

    companion object {
        fun forScore(score: Int): RiskLevel = when {
            score >= 75 -> CRITICAL
            score >= 50 -> HIGH
            score >= 25 -> MODERATE
            else -> LOW
        }

        fun fromFirestoreValue(value: String?): RiskLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOW
    }
}

fun RiskLevel.displayLabel(): String = when (this) {
    RiskLevel.LOW -> "Low"
    RiskLevel.MODERATE -> "Moderate"
    RiskLevel.HIGH -> "High"
    RiskLevel.CRITICAL -> "Critical"
}

data class RiskFactor(val label: String = "", val points: Int = 0, val explanation: String = "")

/**
 * One historical snapshot of a risk score, kept so the admin UI can show a real trend line
 * instead of only ever the latest value - "trends over time" is an explicit brief requirement.
 * RiskScoreRepositoryImpl appends one of these to `history` every time RiskRefreshWorker runs,
 * rather than overwriting the score in place.
 */
data class RiskHistoryPoint(val score: Int = 0, val timestamp: Long = System.currentTimeMillis())

data class RiskScore(
    val id: String = "",
    val areaKey: String = "",
    // Human-readable label for the grid cell, e.g. "Near 12.97,77.59" - never a fabricated
    // ward/neighbourhood name, since this project has no real gazetteer data to draw one from.
    val areaLabel: String = "",
    val category: ReportCategory = ReportCategory.OTHER,
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    val score: Int = 0,
    val level: RiskLevel = RiskLevel.LOW,
    val factors: List<RiskFactor> = emptyList(),
    // True when there isn't enough historical data (see RiskScorer.MIN_REPORTS_FOR_SCORE) to
    // produce a meaningful score - the brief explicitly requires showing "Insufficient data"
    // rather than fabricating a number from too little evidence. score/level are left at their
    // defaults (0/LOW) when this is true and must not be interpreted as a real "low risk" result.
    val insufficientData: Boolean = false,
    val contributingReportIds: List<String> = emptyList(),
    val contributingIncidentCount: Int = 0,
    val unresolvedCount: Int = 0,
    val averageResolutionHours: Double = 0.0,
    val isRecurring: Boolean = false,
    val recommendation: String = "",
    val history: List<RiskHistoryPoint> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)
