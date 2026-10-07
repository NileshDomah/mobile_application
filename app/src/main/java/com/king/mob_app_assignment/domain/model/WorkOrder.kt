package com.king.mob_app_assignment.domain.model

/**
 * Brief's suggested 10-state lifecycle, used verbatim - a work order tracks the physical
 * dispatch-and-repair process for an incident, distinct from the incident's own higher-level
 * status (an incident can be IN_PROGRESS while its work order is still ASSIGNED, waiting for the
 * crew to actually head out).
 */
enum class WorkOrderStatus {
    CREATED,
    ASSIGNED,
    ACCEPTED,
    EN_ROUTE,
    INSPECTING,
    IN_PROGRESS,
    COMPLETED,
    AWAITING_VERIFICATION,
    VERIFIED,
    CLOSED;

    companion object {
        fun fromFirestoreValue(value: String?): WorkOrderStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CREATED
    }
}

fun WorkOrderStatus.displayLabel(): String = when (this) {
    WorkOrderStatus.CREATED -> "Created"
    WorkOrderStatus.ASSIGNED -> "Assigned"
    WorkOrderStatus.ACCEPTED -> "Accepted"
    WorkOrderStatus.EN_ROUTE -> "En Route"
    WorkOrderStatus.INSPECTING -> "Inspecting"
    WorkOrderStatus.IN_PROGRESS -> "In Progress"
    WorkOrderStatus.COMPLETED -> "Completed"
    WorkOrderStatus.AWAITING_VERIFICATION -> "Awaiting Verification"
    WorkOrderStatus.VERIFIED -> "Verified"
    WorkOrderStatus.CLOSED -> "Closed"
}

/**
 * A WorkOrder is a genuinely separate Firestore collection from Incident (confirmed with user
 * over Incident+extra-fields) - this allows more than one work order per incident over its
 * lifetime (e.g. a first repair attempt that fails verification, followed by a second work order
 * for re-inspection/re-repair), which folding these fields into Incident directly would not
 * support cleanly.
 */
data class WorkOrder(
    val id: String = "",
    val incidentId: String = "",
    val department: Department? = null,
    // Free text - this coursework project has no real worker/crew directory or user-account
    // type for field staff, so "assigned worker/team" is a plain string an admin types (e.g.
    // "Roads Crew 3", "J. Patel") rather than a reference to a real staff-account entity. See
    // PLACEHOLDERS_TODO.csv for the production alternative (a real staff-user role/collection).
    val assignedTeam: String = "",
    val priority: ReportPriority = ReportPriority.NORMAL,
    val status: WorkOrderStatus = WorkOrderStatus.CREATED,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val inspectionNotes: String = "",
    val repairNotes: String = "",
    val materialsUsed: String = "",
    // Device-local-only, same storage model as report/resolution photos elsewhere in this
    // project (no Firebase Storage - see PLACEHOLDERS_TODO.csv). A work order's evidence photos
    // are captured on whichever admin device closes it out.
    val resolutionPhotoPaths: List<String> = emptyList(),
    val completedAt: Long? = null,
    val history: List<WorkOrderHistoryEntry> = emptyList(),
)

data class WorkOrderHistoryEntry(
    val status: WorkOrderStatus = WorkOrderStatus.CREATED,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val performedByUid: String = "",
)

fun workOrderReferenceCode(workOrderId: String): String {
    if (workOrderId.isBlank()) return "WO-PENDING"
    val slice = workOrderId.filter { it.isLetterOrDigit() }.take(6).uppercase()
    return "WO-$slice"
}
