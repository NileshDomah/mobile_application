package com.king.mob_app_assignment.domain.model

/**
 * Derives a short, human-friendly reference code from a report's Firestore document ID, e.g.
 * "RPT-9F3A2C". Purely presentational - no schema change, no extra Firestore field, and no
 * collision risk beyond what the underlying document ID already guarantees, since it's a
 * deterministic function of the real ID (we just show a friendlier slice of it to the citizen
 * instead of the raw ~20-character Firestore auto-ID). Used anywhere a report is shown to a user
 * so they have something short and quotable to reference in a support conversation, similar to
 * an order number or ticket number in professional service apps.
 */
fun reportReferenceCode(reportId: String): String {
    if (reportId.isBlank()) return "RPT-PENDING"
    val slice = reportId.filter { it.isLetterOrDigit() }.take(6).uppercase()
    return "RPT-$slice"
}
