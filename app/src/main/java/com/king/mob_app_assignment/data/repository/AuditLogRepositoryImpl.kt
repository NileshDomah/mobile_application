package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.AuditEntityType
import com.king.mob_app_assignment.domain.model.AuditLogEntry
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AuditLogRepository {

    private val auditLogCollection = firestore.collection(FirestoreConstants.AUDIT_LOG_COLLECTION)

    override suspend fun record(
        entityType: AuditEntityType,
        entityId: String,
        action: String,
        performedByUid: String,
        previousValue: String,
        newValue: String,
        note: String,
    ): Result<Unit> = runCatching {
        val entry = AuditLogEntry(
            entityType = entityType,
            entityId = entityId,
            action = action,
            performedByUid = performedByUid,
            previousValue = previousValue,
            newValue = newValue,
            note = note,
        )
        auditLogCollection.document().set(entry.toFirestoreMap()).await()
        Unit
    }.onFailure {
        // Deliberately does not rethrow / does not block the caller's actual state change - an
        // audit entry failing to write (e.g. a transient network blip) should never be the
        // reason an admin's real action (a status change, a work order assignment) itself fails
        // or rolls back. The action already happened; only its RECORD of having happened failed,
        // which is logged here for later investigation rather than surfaced as a user-facing error.
        CrashReporter.log("AuditLogRepositoryImpl.record: write failed for $entityType/$entityId action=$action")
        CrashReporter.recordException(it)
    }

    override fun getEntriesForEntity(entityType: AuditEntityType, entityId: String): Flow<List<AuditLogEntry>> = callbackFlow {
        // No server-side orderBy - same reasoning as SupportRepositoryImpl's precedent: a
        // whereEqualTo(entityType)+whereEqualTo(entityId)+orderBy(timestamp) query shape would
        // need its own composite index, and a single entity's own audit trail is a small enough
        // result set to sort client-side instead of provisioning one.
        val listener = auditLogCollection
            .whereEqualTo(FirestoreConstants.AuditLogFields.ENTITY_TYPE, entityType.name)
            .whereEqualTo(FirestoreConstants.AuditLogFields.ENTITY_ID, entityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("AuditLogRepositoryImpl.getEntriesForEntity: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toAuditLogEntryOrNull() }.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getAllEntries(): Flow<List<AuditLogEntry>> = callbackFlow {
        val listener = auditLogCollection
            .orderBy(FirestoreConstants.AuditLogFields.TIMESTAMP, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("AuditLogRepositoryImpl.getAllEntries: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toAuditLogEntryOrNull() })
                }
            }
        awaitClose { listener.remove() }
    }

    private fun DocumentSnapshot.toAuditLogEntryOrNull(): AuditLogEntry? {
        if (!exists()) return null
        return AuditLogEntry(
            id = id,
            entityType = AuditEntityType.entries.firstOrNull {
                it.name == getString(FirestoreConstants.AuditLogFields.ENTITY_TYPE)
            } ?: AuditEntityType.REPORT,
            entityId = getString(FirestoreConstants.AuditLogFields.ENTITY_ID).orEmpty(),
            action = getString(FirestoreConstants.AuditLogFields.ACTION).orEmpty(),
            performedByUid = getString(FirestoreConstants.AuditLogFields.PERFORMED_BY_UID).orEmpty(),
            timestamp = getLong(FirestoreConstants.AuditLogFields.TIMESTAMP) ?: 0L,
            previousValue = getString(FirestoreConstants.AuditLogFields.PREVIOUS_VALUE).orEmpty(),
            newValue = getString(FirestoreConstants.AuditLogFields.NEW_VALUE).orEmpty(),
            note = getString(FirestoreConstants.AuditLogFields.NOTE).orEmpty(),
        )
    }

    private fun AuditLogEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.AuditLogFields.ENTITY_TYPE to entityType.name,
        FirestoreConstants.AuditLogFields.ENTITY_ID to entityId,
        FirestoreConstants.AuditLogFields.ACTION to action,
        FirestoreConstants.AuditLogFields.PERFORMED_BY_UID to performedByUid,
        FirestoreConstants.AuditLogFields.TIMESTAMP to timestamp,
        FirestoreConstants.AuditLogFields.PREVIOUS_VALUE to previousValue,
        FirestoreConstants.AuditLogFields.NEW_VALUE to newValue,
        FirestoreConstants.AuditLogFields.NOTE to note,
    )
}
