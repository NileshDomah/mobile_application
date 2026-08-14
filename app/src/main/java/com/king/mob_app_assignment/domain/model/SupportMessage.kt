package com.king.mob_app_assignment.domain.model

enum class SupportMessageStatus { OPEN, RESOLVED }

/**
 * A citizen-to-admin support message, separate from the reports collection entirely - this is
 * "I have a question/problem about the app or a report" (account issues, confusion about a
 * status, feedback), not a civic issue report itself. Optionally linked to a specific report via
 * [relatedReportId] so an admin has context without the citizen needing to repeat details already
 * on file.
 */
data class SupportMessage(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userDisplayName: String = "",
    val message: String = "",
    // Blank when not tied to a specific report - a general question/feedback message.
    val relatedReportId: String = "",
    val status: SupportMessageStatus = SupportMessageStatus.OPEN,
    // Admin's written reply, if any - shown back to the citizen on their own message. Kept as a
    // single field (not a thread) deliberately: this is a lightweight support-contact feature,
    // not a full two-way messaging system - see PLACEHOLDERS_TODO.csv.
    val adminReply: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
