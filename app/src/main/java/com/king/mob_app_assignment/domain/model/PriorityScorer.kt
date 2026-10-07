package com.king.mob_app_assignment.domain.model

/**
 * Transparent, rule-based priority scoring (0-100) for an incident - combines the AI's own
 * per-report severity guess with the additional factors the brief lists (report count, incident
 * age, recurrence, emergency classification). Every factor is independently visible via
 * [PriorityFactor] - "show why the priority was calculated" is a literal requirement, not just
 * a nice-to-have, so this returns the breakdown alongside the total rather than only the number.
 *
 * Deliberately NOT machine learning - same reasoning as IncidentRepositoryImpl's clustering
 * confidence score: a coursework project with no labeled training data has nothing to train a
 * real model against, so a transparent formula is the honest v1, with the door left open for an
 * ML model to replace this function later without changing any caller (same
 * List<PriorityFactor>-producing contract).
 *
 * Upvoting was removed from the app entirely (client-toggleable counters had a real, unfixed
 * integrity gap - a malicious client could inflate its own bookkeeping without a Cloud Function
 * enforcing the count atomically, and this project stays on Firestore's free Spark plan by
 * choice - see PLACEHOLDERS_TODO.csv). The "Community confirmation" factor that used to read
 * from upvotes was removed rather than left wired to a value that no longer means anything - the
 * 15-point slot it used to occupy is simply unused now, not silently reassigned.
 */
object PriorityScorer {

    private const val MAX_SCORE = 100

    fun score(incident: Incident, reportCount: Int): List<PriorityFactor> {
        val factors = mutableListOf<PriorityFactor>()

        // AI/admin severity is the single largest factor - it's already the most informed signal
        // available (either the vision LLM's read of the photo, or an admin's own judgment),
        // so it anchors the score rather than competing on equal footing with purely
        // volume-based signals like report count.
        val severityPoints = when (incident.severity) {
            ReportPriority.LOW -> 5
            ReportPriority.NORMAL -> 15
            ReportPriority.HIGH -> 30
            ReportPriority.URGENT -> 40
        }
        factors += PriorityFactor(
            label = "Severity",
            points = severityPoints,
            explanation = "${incident.severity.name} severity",
        )

        // Number of independent reports - more citizens reporting the same problem is real-world
        // evidence it's a genuine, widely-felt issue, not a one-off. Capped so a single incident
        // with e.g. 50 reports can't alone blow past what URGENT+everything-else would score.
        val reportPoints = (reportCount * 2).coerceAtMost(20)
        factors += PriorityFactor(
            label = "Report count",
            points = reportPoints,
            explanation = "$reportCount report${if (reportCount == 1) "" else "s"} x 2 points, capped at 20",
        )

        // Incident age - an unresolved incident that's been open a long time deserves rising
        // priority precisely because it's been neglected, not falling priority because it's
        // "old news." Ramps from 0 at day 0 to a cap at 14 days.
        val ageDays = (System.currentTimeMillis() - incident.createdAt) / (24 * 60 * 60 * 1000L)
        val agePoints = (ageDays.toInt()).coerceAtMost(15)
        factors += PriorityFactor(
            label = "Unresolved age",
            points = agePoints,
            explanation = "Open for $ageDays day${if (ageDays == 1L) "" else "s"}, capped at 15",
        )

        // Recurrence: an incident that reopened after being marked RESOLVED/VERIFIED at least
        // once (visible in its own status history) suggests the underlying fix didn't hold -
        // worth flagging as a distinct, higher-priority pattern rather than treating it as
        // "just another new incident."
        val hasRecurred = incident.history.count { it.status == IncidentStatus.RESOLVED || it.status == IncidentStatus.VERIFIED } > 0 &&
            incident.status !in setOf(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED)
        if (hasRecurred) {
            factors += PriorityFactor(label = "Recurrence", points = 10, explanation = "Reopened after a previous resolution attempt")
        }

        // Emergency classification - a simple, explicit override signal rather than trying to
        // infer "this is dangerous" from severity alone: WATER_LEAK and category CUSTOM labels
        // matching known-hazardous keywords (e.g. "gas," "downed power line," "collapse") get an
        // extra weight, since these carry safety risk beyond typical infrastructure inconvenience.
        val isEmergencyCategory = incident.category == ReportCategory.WATER_LEAK ||
            (incident.category == ReportCategory.CUSTOM && EMERGENCY_KEYWORDS.any { incident.customLabel.lowercase().contains(it) })
        if (isEmergencyCategory) {
            factors += PriorityFactor(label = "Emergency classification", points = 10, explanation = "Category flagged as a potential safety hazard")
        }

        return factors
    }

    fun totalScore(factors: List<PriorityFactor>): Int = factors.sumOf { it.points }.coerceIn(0, MAX_SCORE)

    private val EMERGENCY_KEYWORDS = listOf("gas", "power line", "collapse", "sinkhole", "fire", "explosion")
}

/**
 * Intelligent department suggestion based on category - admin can always override (see
 * assignDepartment on both ReportRepository and IncidentRepository, unchanged). This is
 * deliberately a plain lookup table, not a model: category already IS effectively a strong
 * proxy for which department handles it in this app's fixed category set, so there's no real
 * ambiguity to resolve with anything more sophisticated for the CUSTOM case, a keyword match
 * against the free-text label is used as a best-effort guess, falling back to null (no
 * suggestion) rather than guessing wrong with false confidence.
 */
object DepartmentSuggester {

    fun suggestFor(category: ReportCategory, customLabel: String = ""): Department? = when (category) {
        ReportCategory.POTHOLE -> Department.ROADS
        ReportCategory.STREETLIGHT -> Department.ELECTRICAL
        ReportCategory.GARBAGE -> Department.SANITATION
        ReportCategory.WATER_LEAK -> Department.WATER
        ReportCategory.OTHER, ReportCategory.UNCATEGORIZED -> null
        ReportCategory.CUSTOM -> suggestFromKeywords(customLabel)
    }

    private fun suggestFromKeywords(label: String): Department? {
        val words = label.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        return when {
            words.any { it in ROADS_KEYWORDS } -> Department.ROADS
            words.any { it in ELECTRICAL_KEYWORDS } -> Department.ELECTRICAL
            words.any { it in WATER_KEYWORDS } -> Department.WATER
            words.any { it in SANITATION_KEYWORDS } -> Department.SANITATION
            else -> null
        }
    }

    // Whole-word matching against a tokenized label, not a raw substring .contains() check - a
    // substring check let "tree" match inside "street" (a genuine bug caught by
    // DepartmentSuggesterTest: "burst pipe flooding the street" was misrouted to ROADS instead of
    // WATER purely because "street" contains "tree"). Splitting on non-word characters and
    // comparing whole tokens instead means a keyword only matches when it's actually a word in
    // the label, not an incidental substring of an unrelated word.
    private val ROADS_KEYWORDS = setOf("tree", "road", "sidewalk", "pavement", "sign")
    private val ELECTRICAL_KEYWORDS = setOf("light", "power", "wire", "electric")
    private val WATER_KEYWORDS = setOf("water", "pipe", "leak", "flood", "flooding", "drain")
    private val SANITATION_KEYWORDS = setOf("trash", "waste", "garbage", "dump")
}
