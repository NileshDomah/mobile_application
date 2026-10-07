package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.AuditEntityType
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.WorkOrder
import com.king.mob_app_assignment.domain.model.WorkOrderHistoryEntry
import com.king.mob_app_assignment.domain.model.WorkOrderStatus
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkOrderRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auditLogRepository: AuditLogRepository,
) : WorkOrderRepository {

    private val workOrdersCollection = firestore.collection(FirestoreConstants.WORK_ORDERS_COLLECTION)

    override suspend fun createWorkOrder(
        incidentId: String,
        department: Department?,
        assignedTeam: String,
        priority: ReportPriority,
        dueDate: Long?,
        performedByUid: String,
    ): Result<String> = runCatching {
        val docRef = workOrdersCollection.document()
        val workOrder = WorkOrder(
            id = docRef.id,
            incidentId = incidentId,
            department = department,
            assignedTeam = assignedTeam,
            priority = priority,
            status = WorkOrderStatus.CREATED,
            dueDate = dueDate,
            history = listOf(WorkOrderHistoryEntry(status = WorkOrderStatus.CREATED, performedByUid = performedByUid)),
        )
        docRef.set(workOrder.toFirestoreMap()).await()
        auditLogRepository.record(
            entityType = AuditEntityType.WORK_ORDER,
            entityId = docRef.id,
            action = "WORK_ORDER_CREATED",
            performedByUid = performedByUid,
            newValue = "incident=$incidentId department=${department?.name} team=$assignedTeam",
        )
        docRef.id
    }.onFailure {
        CrashReporter.log("WorkOrderRepositoryImpl.createWorkOrder: write failed for incident=$incidentId")
        CrashReporter.recordException(it)
    }

    override fun getWorkOrdersForIncident(incidentId: String): Flow<List<WorkOrder>> = callbackFlow {
        // No server-side orderBy - same reasoning as elsewhere in this project (SupportRepository,
        // AuditLogRepository): a whereEqualTo(incidentId)+orderBy(createdAt) shape would need a
        // second composite index, and a single incident's work orders are a small result set.
        val listener = workOrdersCollection
            .whereEqualTo(FirestoreConstants.WorkOrderFields.INCIDENT_ID, incidentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("WorkOrderRepositoryImpl.getWorkOrdersForIncident: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toWorkOrderOrNull() }.sortedByDescending { it.createdAt })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getAllWorkOrders(): Flow<List<WorkOrder>> = callbackFlow {
        val listener = workOrdersCollection
            .orderBy(FirestoreConstants.WorkOrderFields.CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    CrashReporter.log("WorkOrderRepositoryImpl.getAllWorkOrders: snapshot listener error")
                    CrashReporter.recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toWorkOrderOrNull() })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getWorkOrderById(id: String): Flow<WorkOrder?> = callbackFlow {
        val listener = workOrdersCollection.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                CrashReporter.log("WorkOrderRepositoryImpl.getWorkOrderById: snapshot listener error")
                CrashReporter.recordException(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.toWorkOrderOrNull())
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateStatus(id: String, status: WorkOrderStatus, note: String, performedByUid: String): Result<Unit> = runCatching {
        val snapshot = workOrdersCollection.document(id).get().await()
        val previousStatus = snapshot.toWorkOrderOrNull()?.status?.name.orEmpty()
        workOrdersCollection.document(id).update(
            mapOf(
                FirestoreConstants.WorkOrderFields.STATUS to status.name,
                FirestoreConstants.WorkOrderFields.HISTORY to FieldValue.arrayUnion(
                    WorkOrderHistoryEntry(status = status, note = note, performedByUid = performedByUid).toMap(),
                ),
            ),
        ).await()
        auditLogRepository.record(
            entityType = AuditEntityType.WORK_ORDER,
            entityId = id,
            action = "STATUS_CHANGED",
            performedByUid = performedByUid,
            previousValue = previousStatus,
            newValue = status.name,
            note = note,
        )
        Unit
    }

    override suspend fun updateAssignment(id: String, department: Department?, assignedTeam: String, performedByUid: String): Result<Unit> = runCatching {
        workOrdersCollection.document(id).update(
            mapOf(
                FirestoreConstants.WorkOrderFields.DEPARTMENT to department?.name,
                FirestoreConstants.WorkOrderFields.ASSIGNED_TEAM to assignedTeam,
            ),
        ).await()
        auditLogRepository.record(
            entityType = AuditEntityType.WORK_ORDER,
            entityId = id,
            action = "ASSIGNMENT_CHANGED",
            performedByUid = performedByUid,
            newValue = "department=${department?.name} team=$assignedTeam",
        )
        Unit
    }

    override suspend fun setDueDate(id: String, dueDate: Long?): Result<Unit> = runCatching {
        workOrdersCollection.document(id).update(FirestoreConstants.WorkOrderFields.DUE_DATE, dueDate).await()
        Unit
    }

    override suspend fun addInspectionNotes(id: String, notes: String): Result<Unit> = runCatching {
        workOrdersCollection.document(id).update(FirestoreConstants.WorkOrderFields.INSPECTION_NOTES, notes).await()
        Unit
    }

    override suspend fun addRepairNotes(id: String, notes: String, materialsUsed: String): Result<Unit> = runCatching {
        workOrdersCollection.document(id).update(
            mapOf(
                FirestoreConstants.WorkOrderFields.REPAIR_NOTES to notes,
                FirestoreConstants.WorkOrderFields.MATERIALS_USED to materialsUsed,
            ),
        ).await()
        Unit
    }

    override suspend fun completeWithEvidence(id: String, resolutionPhotoFiles: List<File>, performedByUid: String): Result<Unit> = runCatching {
        // Photos attached -> AWAITING_VERIFICATION (there's evidence to check before this is
        // truly done, matching the brief's "Resolution Evidence -> AI Verification" step
        // ordering); no photos -> straight to COMPLETED, since there's nothing to verify against.
        val nextStatus = if (resolutionPhotoFiles.isNotEmpty()) WorkOrderStatus.AWAITING_VERIFICATION else WorkOrderStatus.COMPLETED
        workOrdersCollection.document(id).update(
            mapOf(
                FirestoreConstants.WorkOrderFields.STATUS to nextStatus.name,
                FirestoreConstants.WorkOrderFields.RESOLUTION_PHOTO_PATHS to resolutionPhotoFiles.map { it.absolutePath },
                FirestoreConstants.WorkOrderFields.COMPLETED_AT to System.currentTimeMillis(),
                FirestoreConstants.WorkOrderFields.HISTORY to FieldValue.arrayUnion(
                    WorkOrderHistoryEntry(status = nextStatus, performedByUid = performedByUid).toMap(),
                ),
            ),
        ).await()
        auditLogRepository.record(
            entityType = AuditEntityType.WORK_ORDER,
            entityId = id,
            action = "COMPLETED_WITH_EVIDENCE",
            performedByUid = performedByUid,
            newValue = nextStatus.name,
            note = "${resolutionPhotoFiles.size} resolution photo(s) attached",
        )
        Unit
    }

    private fun WorkOrderHistoryEntry.toMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.WorkOrderHistoryEntryFields.STATUS to status.name,
        FirestoreConstants.WorkOrderHistoryEntryFields.TIMESTAMP to timestamp,
        FirestoreConstants.WorkOrderHistoryEntryFields.NOTE to note,
        FirestoreConstants.WorkOrderHistoryEntryFields.PERFORMED_BY_UID to performedByUid,
    )

    private fun DocumentSnapshot.toWorkOrderOrNull(): WorkOrder? {
        if (!exists()) return null
        return WorkOrder(
            id = id,
            incidentId = getString(FirestoreConstants.WorkOrderFields.INCIDENT_ID).orEmpty(),
            department = Department.fromFirestoreValue(getString(FirestoreConstants.WorkOrderFields.DEPARTMENT)),
            assignedTeam = getString(FirestoreConstants.WorkOrderFields.ASSIGNED_TEAM).orEmpty(),
            priority = ReportPriority.fromFirestoreValue(getString(FirestoreConstants.WorkOrderFields.PRIORITY)),
            status = WorkOrderStatus.fromFirestoreValue(getString(FirestoreConstants.WorkOrderFields.STATUS)),
            createdAt = getLong(FirestoreConstants.WorkOrderFields.CREATED_AT) ?: 0L,
            dueDate = getLong(FirestoreConstants.WorkOrderFields.DUE_DATE),
            inspectionNotes = getString(FirestoreConstants.WorkOrderFields.INSPECTION_NOTES).orEmpty(),
            repairNotes = getString(FirestoreConstants.WorkOrderFields.REPAIR_NOTES).orEmpty(),
            materialsUsed = getString(FirestoreConstants.WorkOrderFields.MATERIALS_USED).orEmpty(),
            resolutionPhotoPaths = (get(FirestoreConstants.WorkOrderFields.RESOLUTION_PHOTO_PATHS) as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList(),
            completedAt = getLong(FirestoreConstants.WorkOrderFields.COMPLETED_AT),
            history = (get(FirestoreConstants.WorkOrderFields.HISTORY) as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                WorkOrderHistoryEntry(
                    status = WorkOrderStatus.fromFirestoreValue(map[FirestoreConstants.WorkOrderHistoryEntryFields.STATUS] as? String),
                    timestamp = (map[FirestoreConstants.WorkOrderHistoryEntryFields.TIMESTAMP] as? Long) ?: 0L,
                    note = (map[FirestoreConstants.WorkOrderHistoryEntryFields.NOTE] as? String).orEmpty(),
                    performedByUid = (map[FirestoreConstants.WorkOrderHistoryEntryFields.PERFORMED_BY_UID] as? String).orEmpty(),
                )
            }?.sortedBy { it.timestamp } ?: emptyList(),
        )
    }

    private fun WorkOrder.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.WorkOrderFields.INCIDENT_ID to incidentId,
        FirestoreConstants.WorkOrderFields.DEPARTMENT to department?.name,
        FirestoreConstants.WorkOrderFields.ASSIGNED_TEAM to assignedTeam,
        FirestoreConstants.WorkOrderFields.PRIORITY to priority.name,
        FirestoreConstants.WorkOrderFields.STATUS to status.name,
        FirestoreConstants.WorkOrderFields.CREATED_AT to createdAt,
        FirestoreConstants.WorkOrderFields.DUE_DATE to dueDate,
        FirestoreConstants.WorkOrderFields.INSPECTION_NOTES to inspectionNotes,
        FirestoreConstants.WorkOrderFields.REPAIR_NOTES to repairNotes,
        FirestoreConstants.WorkOrderFields.MATERIALS_USED to materialsUsed,
        FirestoreConstants.WorkOrderFields.RESOLUTION_PHOTO_PATHS to resolutionPhotoPaths,
        FirestoreConstants.WorkOrderFields.COMPLETED_AT to completedAt,
        FirestoreConstants.WorkOrderFields.HISTORY to history.map {
            mapOf(
                FirestoreConstants.WorkOrderHistoryEntryFields.STATUS to it.status.name,
                FirestoreConstants.WorkOrderHistoryEntryFields.TIMESTAMP to it.timestamp,
                FirestoreConstants.WorkOrderHistoryEntryFields.NOTE to it.note,
                FirestoreConstants.WorkOrderHistoryEntryFields.PERFORMED_BY_UID to it.performedByUid,
            )
        },
    )
}
