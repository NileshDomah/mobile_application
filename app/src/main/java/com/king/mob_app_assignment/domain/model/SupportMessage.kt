package com.king.mob_app_assignment.domain.model

enum class SupportMessageStatus { OPEN, RESOLVED }

enum class SupportTurnSender { CITIZEN, ADMIN }

/**
 * A citizen-to-admin support ticket, separate from the reports collection entirely - this is
 * "I have a question/problem about the app or a report" (account issues, confusion about a
 * status, feedback), not a civic issue report itself. Optionally linked to a specific report via
 * [relatedReportId] so an admin has context without the citizen needing to repeat details already
 * on file.
 *
 * 2026-09-01: this used to be a single flat message + a single admin reply (see git history/
 * REPORT_NOTES.md - deliberately scoped that way as a lightweight contact feature). It's now a
 * real multi-turn chat thread instead, on explicit request - the citizen can still have several
 * independent tickets open at once (that part of the design is unchanged), but each ticket is now
 * a back-and-forth conversation rather than a single question-and-answer pair. The individual
 * messages themselves live in the /supportMessages/{id}/turns sub-collection (see [SupportTurn]),
 * not on this document - this document is now just the thread's metadata: who it's with, whether
 * it's open or an admin has closed it, and a preview of the most recent turn for the list views
 * (ContactSupportScreen's own ticket list, AdminSupportMessagesScreen's inbox) so they don't need
 * to read every ticket's full turn history just to render a summary row.
 */
data class SupportMessage(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userDisplayName: String = "",
    // Blank when not tied to a specific report - a general question/feedback message.
    val relatedReportId: String = "",
    val status: SupportMessageStatus = SupportMessageStatus.OPEN,
    val timestamp: Long = System.currentTimeMillis(),
    // Denormalized preview of the most recent turn, kept on the parent ticket doc so list screens
    // can render a summary (sender + snippet + when) without opening the turns sub-collection -
    // written by the same transaction/batch that appends a new turn, see
    // SupportRepositoryImpl.sendCitizenTurn/sendAdminReply.
    val lastMessagePreview: String = "",
    val lastMessageSender: SupportTurnSender = SupportTurnSender.CITIZEN,
    val lastMessageAt: Long = 0L,
    // Number of turns from the OTHER party the current viewer hasn't opened this ticket to see
    // yet - unreadByCitizen counts unread admin turns (drives the citizen-facing Profile tab
    // badge), unreadByAdmin counts unread citizen turns (drives the admin inbox's own badge, not
    // yet built into the nav but tracked the same way for when it is). Reset to 0 by
    // SupportRepository.markThreadRead() when the corresponding party opens the thread.
    val unreadByCitizen: Int = 0,
    val unreadByAdmin: Int = 0,
)

/**
 * One message within a support ticket's chat thread - see [SupportMessage]'s doc comment for why
 * these live in a sub-collection rather than as flat fields on the ticket. Lives at
 * /supportMessages/{ticketId}/turns/{turnId}, ordered by [timestamp] ascending (oldest first,
 * standard chat-log order) when rendered.
 */
data class SupportTurn(
    val id: String = "",
    val sender: SupportTurnSender = SupportTurnSender.CITIZEN,
    val senderUid: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
