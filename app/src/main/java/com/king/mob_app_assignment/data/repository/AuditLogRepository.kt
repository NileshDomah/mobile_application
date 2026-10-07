package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.AuditEntityType
import com.king.mob_app_assignment.domain.model.AuditLogEntry
import kotlinx.coroutines.flow.Flow

interface AuditLogRepository {

    // Fire-and-forget from the caller's perspective (failures are logged via CrashReporter, not
    // surfaced as UI errors) - an audit entry failing to write should never block or roll back
    // the actual administrative action it's describing; see AuditLogRepositoryImpl for the
    // reasoning on why this is Result<Unit> rather than throwing.
    suspend fun record(
        entityType: AuditEntityType,
        entityId: String,
        action: String,
        performedByUid: String,
        previousValue: String = "",
        newValue: String = "",
        note: String = "",
    ): Result<Unit>

    fun getEntriesForEntity(entityType: AuditEntityType, entityId: String): Flow<List<AuditLogEntry>>

    // Admin-only (enforced by firestore.rules) - the cross-entity "what has this admin done"/
    // "what happened recently across everything" view, distinct from getEntriesForEntity's
    // scoped-to-one-thing view.
    fun getAllEntries(): Flow<List<AuditLogEntry>>
}
