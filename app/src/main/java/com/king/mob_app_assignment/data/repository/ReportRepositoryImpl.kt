package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.king.mob_app_assignment.data.local.PendingReportDao
import com.king.mob_app_assignment.data.local.PendingReportEntity
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.util.ConnectivityObserver
import com.king.mob_app_assignment.util.CrashReporter
import com.king.mob_app_assignment.util.NotificationHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val pendingReportDao: PendingReportDao,
    private val connectivityObserver: ConnectivityObserver,
    private val notificationHelper: NotificationHelper,
) : ReportRepository {

    private val reportsCollection = firestore.collection(FirestoreConstants.REPORTS_COLLECTION)
    private var statusListenerRegistration: ListenerRegistration? = null
    private val lastKnownStatusByReportId = mutableMapOf<String, String>()

    override fun getAllReports(): Flow<List<Report>> = callbackFlow {
        val listener = reportsCollection
            .orderBy(FirestoreConstants.ReportFields.TIMESTAMP, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toReportOrNull() })
            }
        awaitClose { listener.remove() }
    }

    override fun getReportsForUser(uid: String): Flow<List<Report>> = callbackFlow {
        val listener = reportsCollection
            .whereEqualTo(FirestoreConstants.ReportFields.USER_ID, uid)
            .orderBy(FirestoreConstants.ReportFields.TIMESTAMP, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toReportOrNull() })
            }
        awaitClose { listener.remove() }
    }

    override fun getReportById(id: String): Flow<Report?> = callbackFlow {
        val listener = reportsCollection.document(id).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toReportOrNull())
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateReportStatus(id: String, status: ReportStatus): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(FirestoreConstants.ReportFields.STATUS, status.name)
            .await()
        Unit
    }

    override suspend fun assignDepartment(id: String, department: Department?): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(FirestoreConstants.ReportFields.DEPARTMENT, department?.name)
            .await()
        Unit
    }

    override suspend fun setPriority(id: String, priority: ReportPriority): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(FirestoreConstants.ReportFields.PRIORITY, priority.name)
            .await()
        Unit
    }

    override suspend fun resolveWithNote(
        id: String,
        status: ReportStatus,
        note: String,
        resolutionPhotoFile: File?,
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any?>(
            FirestoreConstants.ReportFields.STATUS to status.name,
            FirestoreConstants.ReportFields.RESOLUTION_NOTE to note,
        )
        if (resolutionPhotoFile != null) {
            updates[FirestoreConstants.ReportFields.RESOLUTION_PHOTO_PATH] = resolutionPhotoFile.absolutePath
        }
        reportsCollection.document(id).update(updates).await()
        Unit
    }

    override suspend fun updateReport(id: String, category: ReportCategory, note: String, customLabel: String): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(
                mapOf(
                    FirestoreConstants.ReportFields.CATEGORY to category.name,
                    FirestoreConstants.ReportFields.CUSTOM_LABEL to customLabel,
                    FirestoreConstants.ReportFields.NOTE to note,
                ),
            )
            .await()
        Unit
    }

    override suspend fun deleteReport(id: String): Result<Unit> = runCatching {
        reportsCollection.document(id).delete().await()
        Unit
    }

    override suspend fun flagForAiReview(id: String, aiCategory: ReportCategory?, aiCustomLabel: String): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(
                mapOf(
                    FirestoreConstants.ReportFields.NEEDS_AI_REVIEW to true,
                    FirestoreConstants.ReportFields.AI_SUGGESTED_CATEGORY to aiCategory?.name,
                    FirestoreConstants.ReportFields.AI_SUGGESTED_CUSTOM_LABEL to aiCustomLabel,
                ),
            )
            .await()
        Unit
    }

    override suspend fun applyAiCategory(id: String, category: ReportCategory, customLabel: String): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(
                mapOf(
                    FirestoreConstants.ReportFields.CATEGORY to category.name,
                    FirestoreConstants.ReportFields.CUSTOM_LABEL to customLabel,
                ),
            )
            .await()
        Unit
    }

    override suspend fun resubmitAfterAiReview(id: String, category: ReportCategory, note: String, customLabel: String): Result<Unit> = runCatching {
        reportsCollection.document(id)
            .update(
                mapOf(
                    FirestoreConstants.ReportFields.CATEGORY to category.name,
                    FirestoreConstants.ReportFields.CUSTOM_LABEL to customLabel,
                    FirestoreConstants.ReportFields.NOTE to note,
                    FirestoreConstants.ReportFields.NEEDS_AI_REVIEW to false,
                    FirestoreConstants.ReportFields.WAS_RESUBMITTED_AFTER_AI_REVIEW to true,
                ),
            )
            .await()
        Unit
    }

    override suspend fun findNearbyRecentReports(
        category: ReportCategory,
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        withinMinutes: Long,
    ): List<Report> {
        val sinceTimestamp = System.currentTimeMillis() - withinMinutes * 60_000L
        val snapshot = reportsCollection
            .whereEqualTo(FirestoreConstants.ReportFields.CATEGORY, category.name)
            .whereGreaterThanOrEqualTo(FirestoreConstants.ReportFields.TIMESTAMP, sinceTimestamp)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toReportOrNull() }
            .filter { haversineMeters(lat, lng, it.latitude, it.longitude) <= radiusMeters }
    }

    override suspend fun submitReport(report: Report, photoFile: File?): Result<Report> {
        if (!connectivityObserver.isOnlineNow()) {
            return runCatching {
                pendingReportDao.insert(
                    PendingReportEntity(
                        userId = report.userId,
                        category = report.category.name,
                        customLabel = report.customLabel,
                        localPhotoPath = photoFile?.absolutePath ?: report.localPhotoPath.orEmpty(),
                        latitude = report.latitude,
                        longitude = report.longitude,
                        address = report.address,
                        headingDegrees = report.headingDegrees,
                        headingCardinal = report.headingCardinal,
                        timestamp = report.timestamp,
                        note = report.note,
                        status = report.status.name,
                    ),
                )
                report
            }
        }
        return createReport(report, photoFile)
    }

    override suspend fun createReport(report: Report): Result<Report> = createReport(report, null)

    override suspend fun syncPendingReports() {
        val pending = pendingReportDao.getAllOnce()
        for (entity in pending) {
            val report = Report(
                userId = entity.userId,
                category = ReportCategory.fromFirestoreValue(entity.category),
                customLabel = entity.customLabel,
                localPhotoPath = entity.localPhotoPath,
                latitude = entity.latitude,
                longitude = entity.longitude,
                address = entity.address,
                headingDegrees = entity.headingDegrees,
                headingCardinal = entity.headingCardinal,
                timestamp = entity.timestamp,
                note = entity.note,
                status = ReportStatus.fromFirestoreValue(entity.status),
            )
            val photoFile = entity.localPhotoPath.takeIf { it.isNotBlank() }?.let { File(it) }
                ?.takeIf { it.exists() }
            val result = createReport(report, photoFile)
            if (result.isSuccess) {
                pendingReportDao.deleteById(entity.id)
            }
        }
    }

    override suspend fun toggleUpvote(id: String, uid: String): Result<Unit> = runCatching {
        val docRef = reportsCollection.document(id)
        val snapshot = docRef.get().await()
        val report = snapshot.toReportOrNull() ?: error("Report $id not found")
        val alreadyUpvoted = uid in report.upvotedByUids
        val updatedUids = if (alreadyUpvoted) {
            report.upvotedByUids - uid
        } else {
            report.upvotedByUids + uid
        }
        docRef.update(
            mapOf(
                FirestoreConstants.ReportFields.UPVOTED_BY_UIDS to updatedUids,
                FirestoreConstants.ReportFields.UPVOTE_COUNT to updatedUids.size,
            ),
        ).await()
        Unit
    }

    override fun startStatusChangeListener(uid: String) {
        stopStatusChangeListener()
        lastKnownStatusByReportId.clear()
        statusListenerRegistration = reportsCollection
            .whereEqualTo(FirestoreConstants.ReportFields.USER_ID, uid)
            .addSnapshotListener { snapshot, _ ->
                val documents = snapshot?.documents.orEmpty()
                for (doc in documents) {
                    val report = doc.toReportOrNull() ?: continue
                    val previousStatus = lastKnownStatusByReportId[report.id]
                    val currentStatus = report.status.name
                    if (previousStatus != null && previousStatus != currentStatus) {
                        notificationHelper.notifyStatusChanged(
                            reportId = report.id,
                            category = report.category.name,
                            newStatus = currentStatus,
                            resolutionNote = report.resolutionNote,
                        )
                    }
                    lastKnownStatusByReportId[report.id] = currentStatus
                }
            }
    }

    override fun stopStatusChangeListener() {
        statusListenerRegistration?.remove()
        statusListenerRegistration = null
    }

    private suspend fun createReport(report: Report, photoFile: File?): Result<Report> = runCatching {
        // Firebase Storage requires the Blaze (billed) plan; skipped for this coursework project.
        // Photos are kept device-local only and referenced by file path, not uploaded to the cloud.
        // See PLACEHOLDERS_TODO.csv for the real-Storage upgrade path.
        val docRef = if (report.id.isBlank()) reportsCollection.document() else reportsCollection.document(report.id)
        val toSave = report.copy(
            id = docRef.id,
            localPhotoPath = photoFile?.absolutePath ?: report.localPhotoPath,
        )
        try {
            docRef.set(toSave.toFirestoreMap()).await()
        } catch (e: Exception) {
            CrashReporter.log("ReportRepositoryImpl.createReport: Firestore write failed for category=${report.category.name}")
            CrashReporter.recordException(e)
            throw e
        }
        toSave
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun DocumentSnapshot.toReportOrNull(): Report? {
        if (!exists()) return null
        return Report(
            id = id,
            userId = getString(FirestoreConstants.ReportFields.USER_ID).orEmpty(),
            category = ReportCategory.fromFirestoreValue(getString(FirestoreConstants.ReportFields.CATEGORY)),
            photoUrl = getString(FirestoreConstants.ReportFields.PHOTO_URL),
            localPhotoPath = getString(FirestoreConstants.ReportFields.LOCAL_PHOTO_PATH),
            latitude = getDouble(FirestoreConstants.ReportFields.LATITUDE) ?: 0.0,
            longitude = getDouble(FirestoreConstants.ReportFields.LONGITUDE) ?: 0.0,
            address = getString(FirestoreConstants.ReportFields.ADDRESS).orEmpty(),
            headingDegrees = (getDouble(FirestoreConstants.ReportFields.HEADING_DEGREES) ?: 0.0).toFloat(),
            headingCardinal = getString(FirestoreConstants.ReportFields.HEADING_CARDINAL).orEmpty(),
            timestamp = getLong(FirestoreConstants.ReportFields.TIMESTAMP) ?: 0L,
            customLabel = getString(FirestoreConstants.ReportFields.CUSTOM_LABEL).orEmpty(),
            note = getString(FirestoreConstants.ReportFields.NOTE).orEmpty(),
            status = ReportStatus.fromFirestoreValue(getString(FirestoreConstants.ReportFields.STATUS)),
            aiSuggestedCategory = getString(FirestoreConstants.ReportFields.AI_SUGGESTED_CATEGORY)
                ?.let { ReportCategory.fromFirestoreValue(it) },
            aiSuggestedCustomLabel = getString(FirestoreConstants.ReportFields.AI_SUGGESTED_CUSTOM_LABEL).orEmpty(),
            needsAiReview = getBoolean(FirestoreConstants.ReportFields.NEEDS_AI_REVIEW) ?: false,
            wasResubmittedAfterAiReview = getBoolean(FirestoreConstants.ReportFields.WAS_RESUBMITTED_AFTER_AI_REVIEW) ?: false,
            department = Department.fromFirestoreValue(getString(FirestoreConstants.ReportFields.DEPARTMENT)),
            priority = ReportPriority.fromFirestoreValue(getString(FirestoreConstants.ReportFields.PRIORITY)),
            resolutionNote = getString(FirestoreConstants.ReportFields.RESOLUTION_NOTE).orEmpty(),
            resolutionPhotoPath = getString(FirestoreConstants.ReportFields.RESOLUTION_PHOTO_PATH),
            upvoteCount = (getLong(FirestoreConstants.ReportFields.UPVOTE_COUNT) ?: 0L).toInt(),
            upvotedByUids = (get(FirestoreConstants.ReportFields.UPVOTED_BY_UIDS) as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList(),
        )
    }

    private fun Report.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.ReportFields.USER_ID to userId,
        FirestoreConstants.ReportFields.CATEGORY to category.name,
        FirestoreConstants.ReportFields.CUSTOM_LABEL to customLabel,
        FirestoreConstants.ReportFields.PHOTO_URL to photoUrl,
        FirestoreConstants.ReportFields.LOCAL_PHOTO_PATH to localPhotoPath,
        FirestoreConstants.ReportFields.LATITUDE to latitude,
        FirestoreConstants.ReportFields.LONGITUDE to longitude,
        FirestoreConstants.ReportFields.ADDRESS to address,
        FirestoreConstants.ReportFields.HEADING_DEGREES to headingDegrees,
        FirestoreConstants.ReportFields.HEADING_CARDINAL to headingCardinal,
        FirestoreConstants.ReportFields.TIMESTAMP to timestamp,
        FirestoreConstants.ReportFields.NOTE to note,
        FirestoreConstants.ReportFields.STATUS to status.name,
        FirestoreConstants.ReportFields.AI_SUGGESTED_CATEGORY to aiSuggestedCategory?.name,
        FirestoreConstants.ReportFields.AI_SUGGESTED_CUSTOM_LABEL to aiSuggestedCustomLabel,
        FirestoreConstants.ReportFields.NEEDS_AI_REVIEW to needsAiReview,
        FirestoreConstants.ReportFields.WAS_RESUBMITTED_AFTER_AI_REVIEW to wasResubmittedAfterAiReview,
        FirestoreConstants.ReportFields.DEPARTMENT to department?.name,
        FirestoreConstants.ReportFields.PRIORITY to priority.name,
        FirestoreConstants.ReportFields.RESOLUTION_NOTE to resolutionNote,
        FirestoreConstants.ReportFields.RESOLUTION_PHOTO_PATH to resolutionPhotoPath,
        FirestoreConstants.ReportFields.UPVOTE_COUNT to upvoteCount,
        FirestoreConstants.ReportFields.UPVOTED_BY_UIDS to upvotedByUids,
    )
}
