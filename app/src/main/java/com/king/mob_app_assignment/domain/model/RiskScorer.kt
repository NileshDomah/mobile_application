package com.king.mob_app_assignment.domain.model

import kotlin.math.roundToInt

/**
 * Computes a transparent 0-100 risk score for one (area, category) bucket from historical report
 * data - "Predictive Infrastructure Intelligence" per the brief. Same philosophy as
 * PriorityScorer: a rule-based weighted formula, not a trained model, with every contributing
 * factor stored and explained rather than presented as an opaque prediction. RiskRefreshWorker
 * calls score() once per (area, category) group found in the current report data; nothing here
 * talks to Firestore directly, so it can be unit-tested as a pure function.
 */
object RiskScorer {

    // Below this many reports in a bucket, there is not enough evidence to say anything
    // meaningful - the brief explicitly requires surfacing "Insufficient data" here rather than
    // computing a score (even a low one) from too little evidence, since a low score computed
    // from e.g. one report would misleadingly look like a confident "this area is fine."
    const val MIN_REPORTS_FOR_SCORE = 3

    // Grid cell size for bucketing reports into an "area" - see areaKeyFor(). ~0.005 degrees of
    // latitude is roughly 550m; coarse by design (no formal ward/zone data exists in this
    // project - see RiskScore.kt's doc and PLACEHOLDERS_TODO.csv).
    private const val GRID_CELL_DEGREES = 0.005

    fun areaKeyFor(latitude: Double, longitude: Double): String {
        val latCell = Math.floor(latitude / GRID_CELL_DEGREES).toInt()
        val lngCell = Math.floor(longitude / GRID_CELL_DEGREES).toInt()
        return "${latCell}_$lngCell"
    }

    fun areaCenterFor(latitude: Double, longitude: Double): Pair<Double, Double> {
        val latCell = Math.floor(latitude / GRID_CELL_DEGREES)
        val lngCell = Math.floor(longitude / GRID_CELL_DEGREES)
        return (latCell + 0.5) * GRID_CELL_DEGREES to (lngCell + 0.5) * GRID_CELL_DEGREES
    }

    /**
     * @param reports all reports already filtered to one (area, category) bucket.
     * @param incidentCount number of distinct incidents these reports have produced (via
     *   incidentId), used as a "did this become a confirmed infrastructure problem" signal
     *   distinct from raw report volume.
     */
    fun score(reports: List<Report>, incidentCount: Int): List<RiskFactor> {
        val factors = mutableListOf<RiskFactor>()
        val total = reports.size

        // Volume - more independent reports in one small area/category is the single strongest
        // signal of a genuine, recurring infrastructure weak point rather than a one-off.
        val volumePoints = (total * 4).coerceAtMost(30)
        factors += RiskFactor(
            label = "Report volume",
            points = volumePoints,
            explanation = "$total report${if (total == 1) "" else "s"} recorded here (4 points each, capped at 30)",
        )

        // Unresolved backlog - reports still open (not RESOLVED) represent live, unaddressed
        // risk, distinct from volume that's already been dealt with.
        val unresolved = reports.count { it.status != ReportStatus.RESOLVED }
        val unresolvedRatio = if (total > 0) unresolved.toDouble() / total else 0.0
        val unresolvedPoints = (unresolvedRatio * 20).roundToInt()
        factors += RiskFactor(
            label = "Unresolved backlog",
            points = unresolvedPoints,
            explanation = "$unresolved of $total reports still unresolved",
        )

        // Severity mix - the average declared priority across all reports in the bucket, so a
        // cluster of URGENT reports scores higher risk than the same count of LOW-priority ones.
        val avgSeverity = reports.map {
            when (it.priority) {
                ReportPriority.LOW -> 0
                ReportPriority.NORMAL -> 1
                ReportPriority.HIGH -> 2
                ReportPriority.URGENT -> 3
            }
        }.average()
        val severityPoints = ((avgSeverity / 3.0) * 20).roundToInt()
        factors += RiskFactor(
            label = "Severity mix",
            points = severityPoints,
            explanation = "Average declared priority across reports here",
        )

        // Confirmed incidents - reports that clustered into an actual Infrastructure Incident are
        // stronger evidence than raw report count alone (an incident required cross-report
        // corroboration to form in the first place, see IncidentRepositoryImpl).
        val incidentPoints = (incidentCount * 5).coerceAtMost(15)
        factors += RiskFactor(
            label = "Confirmed incidents",
            points = incidentPoints,
            explanation = "$incidentCount confirmed incident${if (incidentCount == 1) "" else "s"} formed here (5 points each, capped at 15)",
        )

        // Recent increase - reports in the last 30 days vs the prior 30-60 day window. A rising
        // trend is itself a risk signal independent of the absolute count.
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val recentCount = reports.count { now - it.timestamp <= thirtyDaysMs }
        val priorCount = reports.count { now - it.timestamp > thirtyDaysMs && now - it.timestamp <= 2 * thirtyDaysMs }
        val isIncreasing = recentCount > priorCount && recentCount >= 2
        if (isIncreasing) {
            factors += RiskFactor(
                label = "Recent increase",
                points = 10,
                explanation = "$recentCount reports in the last 30 days vs $priorCount in the prior 30",
            )
        }

        // Slow resolution - a bucket where resolved reports historically took a long time to
        // close suggests a harder-to-fix or lower-priority-in-practice location, itself a risk
        // factor (issues linger longer here than elsewhere).
        val resolutionHours = averageResolutionHours(reports)
        if (resolutionHours != null && resolutionHours > 72) {
            factors += RiskFactor(
                label = "Slow resolution history",
                points = 5,
                explanation = "Average ${(resolutionHours / 24).roundToInt()} days to resolve past reports here",
            )
        }

        return factors
    }

    fun totalScore(factors: List<RiskFactor>): Int = factors.sumOf { it.points }.coerceIn(0, 100)

    /** Average hours between a report's submission and its RESOLVED statusHistory entry, if any resolved reports exist. */
    fun averageResolutionHours(reports: List<Report>): Double? {
        val durations = reports.mapNotNull { report ->
            val resolvedAt = report.statusHistory.firstOrNull { it.status == ReportStatus.RESOLVED }?.timestamp
            resolvedAt?.let { (it - report.timestamp).coerceAtLeast(0L) }
        }
        if (durations.isEmpty()) return null
        return durations.average() / (60.0 * 60 * 1000)
    }

    fun isRecurring(reports: List<Report>): Boolean {
        // A bucket where reports keep appearing after earlier ones were already resolved -
        // distinct from "just a big backlog," this specifically flags the same spot breaking
        // again after being fixed.
        val resolved = reports.filter { it.status == ReportStatus.RESOLVED }
        if (resolved.isEmpty()) return false
        val lastResolvedAt = resolved.mapNotNull { r -> r.statusHistory.firstOrNull { it.status == ReportStatus.RESOLVED }?.timestamp }.maxOrNull()
            ?: return false
        return reports.any { it.timestamp > lastResolvedAt }
    }

    fun recommendationFor(level: RiskLevel, isRecurring: Boolean, unresolvedCount: Int): String = when {
        level == RiskLevel.CRITICAL -> "Prioritize dispatch to this area - repeated, high-severity reports with a large unresolved backlog."
        level == RiskLevel.HIGH && isRecurring -> "Issue has recurred after a previous fix - consider a more permanent repair rather than a temporary patch."
        level == RiskLevel.HIGH -> "Schedule inspection soon - report volume and severity here are significantly above typical levels."
        level == RiskLevel.MODERATE && unresolvedCount > 0 -> "Monitor - moderate risk with $unresolvedCount unresolved report${if (unresolvedCount == 1) "" else "s"}."
        level == RiskLevel.MODERATE -> "Monitor - moderate historical risk, no urgent backlog currently."
        else -> "No elevated action needed based on current data."
    }
}
