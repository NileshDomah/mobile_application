package com.king.mob_app_assignment.data.remote

object FirestoreConstants {

    const val USERS_COLLECTION = "users"
    const val REPORTS_COLLECTION = "reports"

    object UserFields {
        const val UID = "uid"
        const val EMAIL = "email"
        const val DISPLAY_NAME = "displayName"
        const val ROLE = "role"
    }

    object ReportFields {
        const val USER_ID = "userId"
        const val CATEGORY = "category"
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
    }

    object RoleValues {
        const val CITIZEN = "CITIZEN"
        const val ADMIN = "ADMIN"
    }

    fun reportPhotoStoragePath(userId: String, reportId: String): String =
        "report_photos/$userId/$reportId.jpg"
}
