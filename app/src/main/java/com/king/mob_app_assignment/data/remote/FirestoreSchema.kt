package com.king.mob_app_assignment.data.remote

object FirestoreConstants {

    const val USERS_COLLECTION = "users"
    const val REPORTS_COLLECTION = "reports"
    const val USERNAMES_COLLECTION = "usernames"
    const val SUPPORT_MESSAGES_COLLECTION = "supportMessages"

    object UserFields {
        const val UID = "uid"
        const val EMAIL = "email"
        const val DISPLAY_NAME = "displayName"
        const val USERNAME = "username"
        const val ROLE = "role"
    }

    /** Fields of a /usernames/{username} reservation doc - see AuthRepositoryImpl.register(). */
    object UsernameFields {
        const val UID = "uid"
    }

    object SupportMessageFields {
        const val USER_ID = "userId"
        const val USER_EMAIL = "userEmail"
        const val USER_DISPLAY_NAME = "userDisplayName"
        const val MESSAGE = "message"
        const val RELATED_REPORT_ID = "relatedReportId"
        const val STATUS = "status"
        const val ADMIN_REPLY = "adminReply"
        const val TIMESTAMP = "timestamp"
    }

    object ReportFields {
        const val USER_ID = "userId"
        const val CATEGORY = "category"
        const val CUSTOM_LABEL = "customLabel"
        const val PHOTO_URL = "photoUrl"
        const val LOCAL_PHOTO_PATH = "localPhotoPath"
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
        const val UPVOTE_COUNT = "upvoteCount"
        const val UPVOTED_BY_UIDS = "upvotedByUids"
        const val STATUS_HISTORY = "statusHistory"
        const val MEDIA_TYPE = "mediaType"
        const val LOCAL_VIDEO_PATH = "localVideoPath"
        const val VIDEO_URL = "videoUrl"
        const val IS_ANONYMOUS = "isAnonymous"
    }

    /** Keys inside each map entry of [ReportFields.STATUS_HISTORY]. */
    object StatusHistoryEntryFields {
        const val STATUS = "status"
        const val TIMESTAMP = "timestamp"
        const val NOTE = "note"
    }

    object RoleValues {
        const val CITIZEN = "CITIZEN"
        const val ADMIN = "ADMIN"
    }

    fun reportPhotoStoragePath(userId: String, reportId: String): String =
        "report_photos/$userId/$reportId.jpg"
}
