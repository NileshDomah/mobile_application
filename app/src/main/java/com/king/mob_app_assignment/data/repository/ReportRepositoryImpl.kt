package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.king.mob_app_assignment.data.local.PendingReportDao
import com.king.mob_app_assignment.data.local.PendingReportEntity
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.util.ConnectivityObserver
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
        docRef.set(toSave.toFirestoreMap()).await()
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
            note = getString(FirestoreConstants.ReportFields.NOTE).orEmpty(),
            status = ReportStatus.fromFirestoreValue(getString(FirestoreConstants.ReportFields.STATUS)),
        )
    }

    private fun Report.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.ReportFields.USER_ID to userId,
        FirestoreConstants.ReportFields.CATEGORY to category.name,
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
    )
}
