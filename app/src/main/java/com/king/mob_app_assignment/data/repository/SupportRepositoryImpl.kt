package com.king.mob_app_assignment.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.SupportMessage
import com.king.mob_app_assignment.domain.model.SupportMessageStatus
import com.king.mob_app_assignment.domain.model.SupportTurn
import com.king.mob_app_assignment.domain.model.SupportTurnSender
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val PREVIEW_MAX_LENGTH = 80

@Singleton
class SupportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) : SupportRepository {

    private val ticketsCollection = firestore.collection(FirestoreConstants.SUPPORT_MESSAGES_COLLECTION)

    private fun turnsCollection(ticketId: String) =
        ticketsCollection.document(ticketId).collection(FirestoreConstants.SUPPORT_TURNS_SUBCOLLECTION)

    override suspend fun startTicket(ticket: SupportMessage, openingMessage: String): Result<String> = runCatching {
        val docRef = ticketsCollection.document()
        val preview = openingMessage.take(PREVIEW_MAX_LENGTH)
        val fullTicket = ticket.copy(
            id = docRef.id,
            lastMessagePreview = preview,
            lastMessageSender = SupportTurnSender.CITIZEN,
            lastMessageAt = ticket.timestamp,
        )
        // Not a batch/transaction: the ticket doc and its first turn are two separate writes, but
        // both are created fresh by this same call with no pre-existing state either could
        // conflict with - a partial failure here (ticket written, turn write fails) would be
        // visible as an empty-looking ticket with no messages, recoverable by the citizen simply
        // retrying, not a data-integrity issue the way a partial update to existing data would be.
        docRef.set(fullTicket.toFirestoreMap()).await()
        turnsCollection(docRef.id).document().set(
            SupportTurn(
                sender = SupportTurnSender.CITIZEN,
                senderUid = ticket.userId,
                text = openingMessage,
                timestamp = ticket.timestamp,
            ).toFirestoreMap(),
        ).await()
        docRef.id
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.startTicket: write failed")
        CrashReporter.recordException(it)
    }

    // Deliberately no server-side orderBy here (unlike ReportRepositoryImpl's equivalent
    // queries) - whereEqualTo(userId) + orderBy(lastMessageAt) would need its own Firestore
    // composite index, same class of issue as the reports-collection FAILED_PRECONDITION bug
    // documented in REPORT_NOTES.md. Support tickets are low-volume per user, so sorting the
    // small result set client-side avoids provisioning a second index for a lightweight feature.
    override fun getTicketsForUser(uid: String): Flow<List<SupportMessage>> = callbackFlow {
        val listener = ticketsCollection
            .whereEqualTo(FirestoreConstants.SupportMessageFields.USER_ID, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("SupportRepositoryImpl.getTicketsForUser: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { it.toSupportMessageOrNull() }
                            .sortedByDescending { it.lastMessageAt },
                    )
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getAllTickets(): Flow<List<SupportMessage>> = callbackFlow {
        val listener = ticketsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("SupportRepositoryImpl.getAllTickets: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { it.toSupportMessageOrNull() }
                            .sortedByDescending { it.lastMessageAt },
                    )
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getTurnsForTicket(ticketId: String): Flow<List<SupportTurn>> = callbackFlow {
        val listener = turnsCollection(ticketId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("SupportRepositoryImpl.getTurnsForTicket: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { it.toSupportTurnOrNull() }
                            .sortedBy { it.timestamp },
                    )
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendCitizenTurn(ticketId: String, text: String): Result<Unit> = runCatching {
        val ticketSnapshot = ticketsCollection.document(ticketId).get().await()
        val senderUid = ticketSnapshot.getString(FirestoreConstants.SupportMessageFields.USER_ID).orEmpty()
        appendTurn(ticketId, SupportTurnSender.CITIZEN, senderUid, text)
        // Sending a new message reopens a closed ticket - see the interface doc comment for why.
        ticketsCollection.document(ticketId).update(
            mapOf(
                FirestoreConstants.SupportMessageFields.STATUS to SupportMessageStatus.OPEN.name,
                FirestoreConstants.SupportMessageFields.LAST_MESSAGE_PREVIEW to text.take(PREVIEW_MAX_LENGTH),
                FirestoreConstants.SupportMessageFields.LAST_MESSAGE_SENDER to SupportTurnSender.CITIZEN.name,
                FirestoreConstants.SupportMessageFields.LAST_MESSAGE_AT to System.currentTimeMillis(),
                FirestoreConstants.SupportMessageFields.UNREAD_BY_ADMIN to FieldValue.increment(1),
            ),
        ).await()
        Unit
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.sendCitizenTurn: write failed")
        CrashReporter.recordException(it)
    }

    override suspend fun sendAdminReply(ticketId: String, text: String): Result<Unit> = runCatching {
        val adminUid = firebaseAuth.currentUser?.uid.orEmpty()
        appendTurn(ticketId, SupportTurnSender.ADMIN, adminUid, text)
        ticketsCollection.document(ticketId).update(
            mapOf(
                FirestoreConstants.SupportMessageFields.LAST_MESSAGE_PREVIEW to text.take(PREVIEW_MAX_LENGTH),
                FirestoreConstants.SupportMessageFields.LAST_MESSAGE_SENDER to SupportTurnSender.ADMIN.name,
                FirestoreConstants.SupportMessageFields.LAST_MESSAGE_AT to System.currentTimeMillis(),
                FirestoreConstants.SupportMessageFields.UNREAD_BY_CITIZEN to FieldValue.increment(1),
            ),
        ).await()
        Unit
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.sendAdminReply: write failed")
        CrashReporter.recordException(it)
    }

    override suspend fun closeTicket(ticketId: String): Result<Unit> = runCatching {
        ticketsCollection.document(ticketId)
            .update(FirestoreConstants.SupportMessageFields.STATUS, SupportMessageStatus.RESOLVED.name)
            .await()
        Unit
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.closeTicket: write failed")
        CrashReporter.recordException(it)
    }

    override suspend fun markTicketRead(ticketId: String, viewerIsAdmin: Boolean): Result<Unit> = runCatching {
        val field = if (viewerIsAdmin) {
            FirestoreConstants.SupportMessageFields.UNREAD_BY_ADMIN
        } else {
            FirestoreConstants.SupportMessageFields.UNREAD_BY_CITIZEN
        }
        ticketsCollection.document(ticketId).update(field, 0).await()
        Unit
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.markTicketRead: write failed")
        CrashReporter.recordException(it)
    }

    override suspend fun deleteTicket(ticketId: String): Result<Unit> = runCatching {
        // No cascading delete in Firestore - every turn must be deleted individually before the
        // parent ticket document, see the interface's own doc comment for why in that order.
        val turnDocs = turnsCollection(ticketId).get().await().documents
        for (turnDoc in turnDocs) {
            turnDoc.reference.delete().await()
        }
        ticketsCollection.document(ticketId).delete().await()
        Unit
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.deleteTicket: delete failed")
        CrashReporter.recordException(it)
    }

    private suspend fun appendTurn(ticketId: String, sender: SupportTurnSender, senderUid: String, text: String) {
        turnsCollection(ticketId).document().set(
            SupportTurn(sender = sender, senderUid = senderUid, text = text).toFirestoreMap(),
        ).await()
    }

    private fun DocumentSnapshot.toSupportMessageOrNull(): SupportMessage? {
        if (!exists()) return null
        return SupportMessage(
            id = id,
            userId = getString(FirestoreConstants.SupportMessageFields.USER_ID).orEmpty(),
            userEmail = getString(FirestoreConstants.SupportMessageFields.USER_EMAIL).orEmpty(),
            userDisplayName = getString(FirestoreConstants.SupportMessageFields.USER_DISPLAY_NAME).orEmpty(),
            relatedReportId = getString(FirestoreConstants.SupportMessageFields.RELATED_REPORT_ID).orEmpty(),
            status = SupportMessageStatus.entries.firstOrNull {
                it.name == getString(FirestoreConstants.SupportMessageFields.STATUS)
            } ?: SupportMessageStatus.OPEN,
            timestamp = getLong(FirestoreConstants.SupportMessageFields.TIMESTAMP) ?: 0L,
            lastMessagePreview = getString(FirestoreConstants.SupportMessageFields.LAST_MESSAGE_PREVIEW).orEmpty(),
            lastMessageSender = SupportTurnSender.entries.firstOrNull {
                it.name == getString(FirestoreConstants.SupportMessageFields.LAST_MESSAGE_SENDER)
            } ?: SupportTurnSender.CITIZEN,
            lastMessageAt = getLong(FirestoreConstants.SupportMessageFields.LAST_MESSAGE_AT) ?: 0L,
            unreadByCitizen = getLong(FirestoreConstants.SupportMessageFields.UNREAD_BY_CITIZEN)?.toInt() ?: 0,
            unreadByAdmin = getLong(FirestoreConstants.SupportMessageFields.UNREAD_BY_ADMIN)?.toInt() ?: 0,
        )
    }

    private fun SupportMessage.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.SupportMessageFields.USER_ID to userId,
        FirestoreConstants.SupportMessageFields.USER_EMAIL to userEmail,
        FirestoreConstants.SupportMessageFields.USER_DISPLAY_NAME to userDisplayName,
        FirestoreConstants.SupportMessageFields.RELATED_REPORT_ID to relatedReportId,
        FirestoreConstants.SupportMessageFields.STATUS to status.name,
        FirestoreConstants.SupportMessageFields.TIMESTAMP to timestamp,
        FirestoreConstants.SupportMessageFields.LAST_MESSAGE_PREVIEW to lastMessagePreview,
        FirestoreConstants.SupportMessageFields.LAST_MESSAGE_SENDER to lastMessageSender.name,
        FirestoreConstants.SupportMessageFields.LAST_MESSAGE_AT to lastMessageAt,
        FirestoreConstants.SupportMessageFields.UNREAD_BY_CITIZEN to unreadByCitizen,
        FirestoreConstants.SupportMessageFields.UNREAD_BY_ADMIN to unreadByAdmin,
    )

    private fun DocumentSnapshot.toSupportTurnOrNull(): SupportTurn? {
        if (!exists()) return null
        return SupportTurn(
            id = id,
            sender = SupportTurnSender.entries.firstOrNull {
                it.name == getString(FirestoreConstants.SupportTurnFields.SENDER)
            } ?: SupportTurnSender.CITIZEN,
            senderUid = getString(FirestoreConstants.SupportTurnFields.SENDER_UID).orEmpty(),
            text = getString(FirestoreConstants.SupportTurnFields.TEXT).orEmpty(),
            timestamp = getLong(FirestoreConstants.SupportTurnFields.TIMESTAMP) ?: 0L,
        )
    }

    private fun SupportTurn.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.SupportTurnFields.SENDER to sender.name,
        FirestoreConstants.SupportTurnFields.SENDER_UID to senderUid,
        FirestoreConstants.SupportTurnFields.TEXT to text,
        FirestoreConstants.SupportTurnFields.TIMESTAMP to timestamp,
    )
}
