package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.SupportMessage
import com.king.mob_app_assignment.domain.model.SupportTurn
import kotlinx.coroutines.flow.Flow

interface SupportRepository {

    // Creates a brand-new ticket with its first turn (the citizen's opening message) - the
    // one-doc-per-question model is unchanged from before the chat redesign, only what happens
    // WITHIN one ticket changed (see SupportMessage's doc comment). Returns the new ticket's id
    // on success, so the caller can navigate straight into its thread.
    suspend fun startTicket(ticket: SupportMessage, openingMessage: String): Result<String>

    // Citizen-facing: only the signed-in user's own tickets (list view - ContactSupportScreen's
    // own ticket list), sorted by lastMessageAt descending so an active conversation surfaces
    // first, same convention as a real messaging app's conversation list.
    fun getTicketsForUser(uid: String): Flow<List<SupportMessage>>

    // Admin-facing: every support ticket across all users.
    fun getAllTickets(): Flow<List<SupportMessage>>

    // Live turn history for one ticket, oldest first - backs both ContactSupportScreen's open
    // thread and AdminSupportMessagesScreen's own thread view.
    fun getTurnsForTicket(ticketId: String): Flow<List<SupportTurn>>

    // Appends a citizen turn to an existing ticket and reopens it if an admin had closed it
    // (sending a new message is treated as "I still need help with this," same as replying to a
    // closed conversation in a real chat app reopens it from the sender's side).
    suspend fun sendCitizenTurn(ticketId: String, text: String): Result<Unit>

    // Admin-only (enforced by firestore.rules isAdmin()) - appends an admin turn WITHOUT closing
    // the ticket, a genuinely new action that didn't exist before (previously replying and
    // resolving were the same atomic write - see AdminSupportMessagesViewModel).
    suspend fun sendAdminReply(ticketId: String, text: String): Result<Unit>

    // Admin-only - closes the ticket without necessarily sending a final message (the previous
    // combined reply+resolve button still works from the UI layer by calling this right after
    // sendAdminReply, but the two are now independently callable).
    suspend fun closeTicket(ticketId: String): Result<Unit>

    // Marks every turn from the OTHER party as read for the given viewer, zeroing
    // unreadByCitizen (viewerIsAdmin = false) or unreadByAdmin (viewerIsAdmin = true) - called
    // when a screen opens a ticket's thread, mirroring how a real chat app clears a
    // conversation's unread badge the moment you open it.
    suspend fun markTicketRead(ticketId: String, viewerIsAdmin: Boolean): Result<Unit>

    // Admin-only, and only once the ticket is RESOLVED (enforced by firestore.rules - see its own
    // doc comment for why). Deletes every turn in the ticket's /turns sub-collection first, then
    // the ticket document itself - Firestore has no cascading delete, so a real cleanup action
    // must do both, in that order, or a delete failing partway through the turns would still
    // leave the parent rule's own delete gate intact for a retry (deleting the parent first would
    // orphan any remaining turns with no ticket left to read them through).
    suspend fun deleteTicket(ticketId: String): Result<Unit>
}
