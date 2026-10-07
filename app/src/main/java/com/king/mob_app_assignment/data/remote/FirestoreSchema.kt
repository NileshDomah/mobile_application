package com.king.mob_app_assignment.data.remote

object FirestoreConstants {

    const val USERS_COLLECTION = "users"
    const val REPORTS_COLLECTION = "reports"
    const val USERNAMES_COLLECTION = "usernames"
    const val EMAILS_COLLECTION = "emails"
    const val SUPPORT_MESSAGES_COLLECTION = "supportMessages"
    const val INCIDENTS_COLLECTION = "incidents"
    const val WORK_ORDERS_COLLECTION = "workOrders"
    const val AUDIT_LOG_COLLECTION = "auditLog"
    const val RISK_SCORES_COLLECTION = "riskScores"

    // A minimal, pre-auth-readable lookup doc keyed by uid, containing ONLY email + username -
    // never role, displayName, or anything else. Exists specifically so
    // resolveUsernameToEmail()/resolveEmailToUsername() never need to read the full /users/{uid}
    // document (which now requires being signed in as that user or an admin, see
    // firestore.rules). Written once at registration time, alongside the real user doc, in the
    // same transaction. See PLACEHOLDERS_TODO.csv for why this still isn't a full fix (a real
    // production system would move this lookup behind a Cloud Function so a client never reads
    // ANY per-user document pre-auth, not even this narrowed one) - it does meaningfully shrink
    // what a pre-auth caller can learn about an arbitrary uid from "everything on the profile" to
    // "just email and username," neither of which is sensitive on its own.
    const val LOGIN_LOOKUP_COLLECTION = "loginLookup"

    object UserFields {
        const val UID = "uid"
        const val EMAIL = "email"
        const val DISPLAY_NAME = "displayName"
        const val USERNAME = "username"
        const val PHONE_NUMBER = "phoneNumber"
        const val ROLE = "role"
        const val FCM_TOKEN = "fcmToken"
    }

    /** Fields of a /usernames/{username} reservation doc - see AuthRepositoryImpl.register(). */
    object UsernameFields {
        const val UID = "uid"
    }

    /**
     * Fields of an /emails/{normalizedEmail} reservation doc - mirrors /usernames/{username}
     * exactly, but keyed by email instead, so resolveEmailToUsername() (the "forgot username"
     * flow) can look up a single doc by a caller-supplied email BEFORE they're signed in,
     * the same way resolveUsernameToEmail() already looks up a single doc by a caller-supplied
     * username before sign-in. See AuthRepositoryImpl.register().
     */
    object EmailFields {
        const val UID = "uid"
    }

    /** Fields of a /loginLookup/{uid} doc - see LOGIN_LOOKUP_COLLECTION's doc for why this exists. */
    object LoginLookupFields {
        const val EMAIL = "email"
        const val USERNAME = "username"
    }

    object SupportMessageFields {
        const val USER_ID = "userId"
        const val USER_EMAIL = "userEmail"
        const val USER_DISPLAY_NAME = "userDisplayName"
        const val RELATED_REPORT_ID = "relatedReportId"
        const val STATUS = "status"
        const val TIMESTAMP = "timestamp"
        const val LAST_MESSAGE_PREVIEW = "lastMessagePreview"
        const val LAST_MESSAGE_SENDER = "lastMessageSender"
        const val LAST_MESSAGE_AT = "lastMessageAt"
        const val UNREAD_BY_CITIZEN = "unreadByCitizen"
        const val UNREAD_BY_ADMIN = "unreadByAdmin"
    }

    // Sub-collection: /supportMessages/{ticketId}/turns/{turnId} - see SupportTurn's own doc
    // comment for why individual chat messages live here instead of as flat fields on the
    // ticket document.
    const val SUPPORT_TURNS_SUBCOLLECTION = "turns"

    object SupportTurnFields {
        const val SENDER = "sender"
        const val SENDER_UID = "senderUid"
        const val TEXT = "text"
        const val TIMESTAMP = "timestamp"
    }

    object ReportFields {
        const val USER_ID = "userId"
        // Denormalized from the reporting user's own User.displayName at submit time, purely so
        // admins can see who filed a report without a per-report lookup of /users/{userId} (which
        // would also need every admin-facing report list/detail screen to join against that
        // collection). Never populated for an anonymous report (isAnonymous == true) - see that
        // field's own doc for why there is no owner identity to show at all in that case. Not
        // kept in sync if the user later changes their display name - this is a point-in-time
        // snapshot of who filed the report, matching how the rest of a report's captured details
        // (photo, location, timestamp) already work.
        const val REPORTER_NAME = "reporterName"
        const val CATEGORY = "category"
        const val CUSTOM_LABEL = "customLabel"
        const val PHOTO_URL = "photoUrl"
        const val LOCAL_PHOTO_PATH = "localPhotoPath"
        const val ADDITIONAL_LOCAL_PHOTO_PATHS = "additionalLocalPhotoPaths"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val ADDRESS = "address"
        const val HEADING_DEGREES = "headingDegrees"
        const val HEADING_CARDINAL = "headingCardinal"
        const val TIMESTAMP = "timestamp"
        const val NOTE = "note"
        const val STATUS = "status"
        const val AI_SUGGESTED_CATEGORY = "aiSuggestedCategory"
        const val AI_SUGGESTED_CUSTOM_LABEL = "aiSuggestedCustomLabel"
        const val NEEDS_AI_REVIEW = "needsAiReview"
        const val WAS_RESUBMITTED_AFTER_AI_REVIEW = "wasResubmittedAfterAiReview"
        const val DEPARTMENT = "department"
        const val PRIORITY = "priority"
        const val RESOLUTION_NOTE = "resolutionNote"
        const val RESOLUTION_PHOTO_PATH = "resolutionPhotoPath"
        const val STATUS_HISTORY = "statusHistory"
        const val MEDIA_TYPE = "mediaType"
        const val LOCAL_VIDEO_PATH = "localVideoPath"
        const val VIDEO_URL = "videoUrl"
        const val IS_ANONYMOUS = "isAnonymous"
        const val INCIDENT_ID = "incidentId"
    }

    /** Keys inside each map entry of [ReportFields.STATUS_HISTORY]. */
    object StatusHistoryEntryFields {
        const val STATUS = "status"
        const val TIMESTAMP = "timestamp"
        const val NOTE = "note"
    }

    object IncidentFields {
        const val TITLE = "title"
        const val CATEGORY = "category"
        const val CUSTOM_LABEL = "customLabel"
        const val STATUS = "status"
        const val SEVERITY = "severity"
        const val DEPARTMENT = "department"
        const val CENTER_LATITUDE = "centerLatitude"
        const val CENTER_LONGITUDE = "centerLongitude"
        const val ADDRESS = "address"
        const val REPORT_COUNT = "reportCount"
        const val MEMBERS = "members"
        const val HISTORY = "history"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val VALIDATION_NOTE = "validationNote"
        const val PRIORITY_SCORE = "priorityScore"
        const val PRIORITY_FACTORS = "priorityFactors"
        const val VALIDATION_STATE = "validationState"
    }

    /** Keys inside each map entry of [IncidentFields.PRIORITY_FACTORS] - see PriorityScorer. */
    object PriorityFactorFields {
        const val LABEL = "label"
        const val POINTS = "points"
        const val EXPLANATION = "explanation"
    }

    object WorkOrderFields {
        const val INCIDENT_ID = "incidentId"
        const val DEPARTMENT = "department"
        const val ASSIGNED_TEAM = "assignedTeam"
        const val PRIORITY = "priority"
        const val STATUS = "status"
        const val CREATED_AT = "createdAt"
        const val DUE_DATE = "dueDate"
        const val INSPECTION_NOTES = "inspectionNotes"
        const val REPAIR_NOTES = "repairNotes"
        const val MATERIALS_USED = "materialsUsed"
        const val RESOLUTION_PHOTO_PATHS = "resolutionPhotoPaths"
        const val COMPLETED_AT = "completedAt"
        const val HISTORY = "history"
    }

    /** Keys inside each map entry of [WorkOrderFields.HISTORY]. */
    object WorkOrderHistoryEntryFields {
        const val STATUS = "status"
        const val TIMESTAMP = "timestamp"
        const val NOTE = "note"
        const val PERFORMED_BY_UID = "performedByUid"
    }

    object AuditLogFields {
        const val ENTITY_TYPE = "entityType"
        const val ENTITY_ID = "entityId"
        const val ACTION = "action"
        const val PERFORMED_BY_UID = "performedByUid"
        const val TIMESTAMP = "timestamp"
        const val PREVIOUS_VALUE = "previousValue"
        const val NEW_VALUE = "newValue"
        const val NOTE = "note"
    }

    object RiskScoreFields {
        const val AREA_KEY = "areaKey"
        const val AREA_LABEL = "areaLabel"
        const val CATEGORY = "category"
        const val CENTER_LATITUDE = "centerLatitude"
        const val CENTER_LONGITUDE = "centerLongitude"
        const val SCORE = "score"
        const val LEVEL = "level"
        const val FACTORS = "factors"
        const val INSUFFICIENT_DATA = "insufficientData"
        const val CONTRIBUTING_REPORT_IDS = "contributingReportIds"
        const val CONTRIBUTING_INCIDENT_COUNT = "contributingIncidentCount"
        const val UNRESOLVED_COUNT = "unresolvedCount"
        const val AVERAGE_RESOLUTION_HOURS = "averageResolutionHours"
        const val IS_RECURRING = "isRecurring"
        const val RECOMMENDATION = "recommendation"
        const val HISTORY = "history"
        const val UPDATED_AT = "updatedAt"
    }

    /** Keys inside each map entry of [RiskScoreFields.FACTORS]. */
    object RiskFactorFields {
        const val LABEL = "label"
        const val POINTS = "points"
        const val EXPLANATION = "explanation"
    }

    /** Keys inside each map entry of [RiskScoreFields.HISTORY]. */
    object RiskHistoryPointFields {
        const val SCORE = "score"
        const val TIMESTAMP = "timestamp"
    }

    /** Keys inside each map entry of [IncidentFields.MEMBERS]. */
    object IncidentMemberFields {
        const val REPORT_ID = "reportId"
        const val SOURCE = "source"
        const val CONFIDENCE = "confidence"
        const val ADDED_AT = "addedAt"
    }

    /** Keys inside each map entry of [IncidentFields.HISTORY]. */
    object IncidentHistoryEntryFields {
        const val STATUS = "status"
        const val TIMESTAMP = "timestamp"
        const val NOTE = "note"
        const val PERFORMED_BY_UID = "performedByUid"
    }

    object RoleValues {
        const val CITIZEN = "CITIZEN"
        const val ADMIN = "ADMIN"
    }

    fun reportPhotoStoragePath(userId: String, reportId: String): String =
        "report_photos/$userId/$reportId.jpg"
}
