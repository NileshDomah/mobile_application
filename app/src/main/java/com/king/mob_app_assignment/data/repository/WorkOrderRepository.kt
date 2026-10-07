package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.WorkOrder
import com.king.mob_app_assignment.domain.model.WorkOrderStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface WorkOrderRepository {

    // Admin-only - creates a new work order for an incident. An incident can have more than one
    // work order over its lifetime (e.g. a failed repair followed by a second attempt), so this
    // is additive, not upsert-by-incidentId.
    suspend fun createWorkOrder(
        incidentId: String,
        department: Department?,
        assignedTeam: String,
        priority: ReportPriority,
        dueDate: Long?,
        performedByUid: String,
    ): Result<String>

    fun getWorkOrdersForIncident(incidentId: String): Flow<List<WorkOrder>>

    fun getAllWorkOrders(): Flow<List<WorkOrder>>

    fun getWorkOrderById(id: String): Flow<WorkOrder?>

    suspend fun updateStatus(id: String, status: WorkOrderStatus, note: String, performedByUid: String): Result<Unit>

    suspend fun updateAssignment(id: String, department: Department?, assignedTeam: String, performedByUid: String): Result<Unit>

    suspend fun setDueDate(id: String, dueDate: Long?): Result<Unit>

    suspend fun addInspectionNotes(id: String, notes: String): Result<Unit>

    suspend fun addRepairNotes(id: String, notes: String, materialsUsed: String): Result<Unit>

    // Marks COMPLETED (or AWAITING_VERIFICATION if photos are attached, matching the brief's
    // "Resolution Evidence -> AI Verification" step ordering) and stamps completedAt.
    suspend fun completeWithEvidence(id: String, resolutionPhotoFiles: List<File>, performedByUid: String): Result<Unit>
}
