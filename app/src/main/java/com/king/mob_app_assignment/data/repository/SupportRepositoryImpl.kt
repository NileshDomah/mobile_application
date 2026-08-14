package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.SupportMessage
import com.king.mob_app_assignment.domain.model.SupportMessageStatus
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SupportRepository {

    private val messagesCollection = firestore.collection(FirestoreConstants.SUPPORT_MESSAGES_COLLECTION)

    override suspend fun sendMessage(message: SupportMessage): Result<Unit> = runCatching {
        val docRef = messagesCollection.document()
        docRef.set(message.copy(id = docRef.id).toFirestoreMap()).await()
        Unit
    }.onFailure {
        CrashReporter.log("SupportRepositoryImpl.sendMessage: write failed")
        CrashReporter.recordException(it)
    }

    // Deliberately no server-side orderBy here (unlike ReportRepositoryImpl's equivalent
    // queries) - whereEqualTo(userId) + orderBy(timestamp) would need its own Firestore
    // composite index, same class of issue as the reports-collection FAILED_PRECONDITION bug
    // documented in REPORT_NOTES.md. Support messages are low-volume per user, so sorting the
    // small result set client-side avoids provisioning a second index for a lightweight feature.
    override fun getMessagesForUser(uid: String): Flow<List<SupportMessage>> = callbackFlow {
        val listener = messagesCollection
            .whereEqualTo(FirestoreConstants.SupportMessageFields.USER_ID, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("SupportRepositoryImpl.getMessagesForUser: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { it.toSupportMessageOrNull() }
                            .sortedByDescending { it.timestamp },
                    )
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getAllMessages(): Flow<List<SupportMessage>> = callbackFlow {
        val listener = messagesCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("SupportRepositoryImpl.getAllMessages: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { it.toSupportMessageOrNull() }
                            .sortedByDescending { it.timestamp },
                    )
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun replyAndResolve(messageId: String, reply: String): Result<Unit> = runCatching {
        messagesCollection.document(messageId)
            .update(
                mapOf(
                    FirestoreConstants.SupportMessageFields.ADMIN_REPLY to reply,
                    FirestoreConstants.SupportMessageFields.STATUS to SupportMessageStatus.RESOLVED.name,
                ),
            )
            .await()
        Unit
    }

    private fun DocumentSnapshot.toSupportMessageOrNull(): SupportMessage? {
        if (!exists()) return null
        return SupportMessage(
            id = id,
            userId = getString(FirestoreConstants.SupportMessageFields.USER_ID).orEmpty(),
            userEmail = getString(FirestoreConstants.SupportMessageFields.USER_EMAIL).orEmpty(),
            userDisplayName = getString(FirestoreConstants.SupportMessageFields.USER_DISPLAY_NAME).orEmpty(),
            message = getString(FirestoreConstants.SupportMessageFields.MESSAGE).orEmpty(),
            relatedReportId = getString(FirestoreConstants.SupportMessageFields.RELATED_REPORT_ID).orEmpty(),
            status = SupportMessageStatus.entries.firstOrNull {
                it.name == getString(FirestoreConstants.SupportMessageFields.STATUS)
            } ?: SupportMessageStatus.OPEN,
            adminReply = getString(FirestoreConstants.SupportMessageFields.ADMIN_REPLY).orEmpty(),
            timestamp = getLong(FirestoreConstants.SupportMessageFields.TIMESTAMP) ?: 0L,
        )
    }

    private fun SupportMessage.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.SupportMessageFields.USER_ID to userId,
        FirestoreConstants.SupportMessageFields.USER_EMAIL to userEmail,
        FirestoreConstants.SupportMessageFields.USER_DISPLAY_NAME to userDisplayName,
        FirestoreConstants.SupportMessageFields.MESSAGE to message,
        FirestoreConstants.SupportMessageFields.RELATED_REPORT_ID to relatedReportId,
        FirestoreConstants.SupportMessageFields.STATUS to status.name,
        FirestoreConstants.SupportMessageFields.ADMIN_REPLY to adminReply,
        FirestoreConstants.SupportMessageFields.TIMESTAMP to timestamp,
    )
}
