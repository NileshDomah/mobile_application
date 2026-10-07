package com.king.mob_app_assignment.domain.model

/** Which kind of entity an audit entry describes a change to - see auditLog collection. */
enum class AuditEntityType { REPORT, INCIDENT, WORK_ORDER }

/**
 * One shared collection (auditLog/{id}) for every important administrative state change across
 * incidents, work orders, and (validation-only) reports - per the brief's explicit requirement
 * that "no important administrative action happens without traceability." A single collection
 * was chosen over per-entity subcollections specifically so there is ONE place to query "what
 * happened to X" or "what has this admin done" across every entity type, including any future
 * entity type this pattern extends to - see REPORT_NOTES.md for the tradeoff discussion.
 *
 * Deliberately NOT a replacement for Incident.history/Report.statusHistory (which stay as
 * before, embedded on their own documents for cheap "show this document's own timeline"
 * rendering) - this is the separate, queryable-across-entities record specifically for
 * accountability ("who did this, when"), which an embedded array on the entity itself can't
 * efficiently answer at the "show me everything Admin X did this week" scale.
 */
data class AuditLogEntry(
    val id: String = "",
    val entityType: AuditEntityType = AuditEntityType.REPORT,
    val entityId: String = "",
    // Short machine-readable action name, e.g. "STATUS_CHANGED", "DEPARTMENT_ASSIGNED",
    // "WORK_ORDER_CREATED" - kept as free text rather than a closed enum since new entity types/
    // actions will keep being added as the platform grows, and a shared log is exactly the place
    // that shouldn't need a schema migration every time a new admin action is introduced.
    val action: String = "",
    val performedByUid: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val previousValue: String = "",
    val newValue: String = "",
    val note: String = "",
)
