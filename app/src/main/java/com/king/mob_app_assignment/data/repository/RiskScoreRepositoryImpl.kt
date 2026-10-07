package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.RiskFactor
import com.king.mob_app_assignment.domain.model.RiskHistoryPoint
import com.king.mob_app_assignment.domain.model.RiskLevel
import com.king.mob_app_assignment.domain.model.RiskScore
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskScoreRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RiskScoreRepository {

    private val riskScoresCollection = firestore.collection(FirestoreConstants.RISK_SCORES_COLLECTION)

    override fun idFor(areaKey: String, category: ReportCategory): String = "${areaKey}_${category.name}"

    override fun getAllRiskScores(): Flow<List<RiskScore>> = callbackFlow {
        // No server-side orderBy - a low-volume collection (one doc per area/category bucket
        // that has ever had enough reports to score), same "sort client-side" tradeoff used
        // throughout this project's newer repositories to avoid provisioning a new composite
        // index for what will realistically never be a large collection.
        val listener = riskScoresCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                CrashReporter.log("RiskScoreRepositoryImpl.getAllRiskScores: snapshot listener error")
                CrashReporter.recordException(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toRiskScoreOrNull() }.sortedByDescending { it.score })
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getRiskScoreById(id: String): Flow<RiskScore?> = callbackFlow {
        val listener = riskScoresCollection.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                CrashReporter.log("RiskScoreRepositoryImpl.getRiskScoreById: snapshot listener error")
                CrashReporter.recordException(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toRiskScoreOrNull())
        }
        awaitClose { listener.remove() }
    }

    override suspend fun upsertRiskScore(riskScore: RiskScore): Result<Unit> = runCatching {
        val docRef = riskScoresCollection.document(riskScore.id)
        val historyPoint = RiskHistoryPoint(score = riskScore.score, timestamp = riskScore.updatedAt)
        val data = riskScore.toFirestoreMap().toMutableMap()
        // History is append-only via arrayUnion, same convention as Incident/WorkOrder history -
        // set() with a plain list would silently drop every prior refresh's history entry.
        data[FirestoreConstants.RiskScoreFields.HISTORY] = FieldValue.arrayUnion(historyPoint.toFirestoreMap())
        docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        Unit
    }.onFailure {
        CrashReporter.log("RiskScoreRepositoryImpl.upsertRiskScore: write failed for ${riskScore.id}")
        CrashReporter.recordException(it)
    }

    private fun DocumentSnapshot.toRiskScoreOrNull(): RiskScore? {
        if (!exists()) return null
        val factorsRaw = get(FirestoreConstants.RiskScoreFields.FACTORS) as? List<*> ?: emptyList<Any>()
        val factors = factorsRaw.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            RiskFactor(
                label = map[FirestoreConstants.RiskFactorFields.LABEL] as? String ?: "",
                points = (map[FirestoreConstants.RiskFactorFields.POINTS] as? Number)?.toInt() ?: 0,
                explanation = map[FirestoreConstants.RiskFactorFields.EXPLANATION] as? String ?: "",
            )
        }
        val historyRaw = get(FirestoreConstants.RiskScoreFields.HISTORY) as? List<*> ?: emptyList<Any>()
        val history = historyRaw.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            RiskHistoryPoint(
                score = (map[FirestoreConstants.RiskHistoryPointFields.SCORE] as? Number)?.toInt() ?: 0,
                timestamp = (map[FirestoreConstants.RiskHistoryPointFields.TIMESTAMP] as? Number)?.toLong() ?: 0L,
            )
        }
        val idsRaw = get(FirestoreConstants.RiskScoreFields.CONTRIBUTING_REPORT_IDS) as? List<*> ?: emptyList<Any>()
        return RiskScore(
            id = id,
            areaKey = getString(FirestoreConstants.RiskScoreFields.AREA_KEY).orEmpty(),
            areaLabel = getString(FirestoreConstants.RiskScoreFields.AREA_LABEL).orEmpty(),
            category = ReportCategory.fromFirestoreValue(getString(FirestoreConstants.RiskScoreFields.CATEGORY)),
            centerLatitude = getDouble(FirestoreConstants.RiskScoreFields.CENTER_LATITUDE) ?: 0.0,
            centerLongitude = getDouble(FirestoreConstants.RiskScoreFields.CENTER_LONGITUDE) ?: 0.0,
            score = getLong(FirestoreConstants.RiskScoreFields.SCORE)?.toInt() ?: 0,
            level = RiskLevel.fromFirestoreValue(getString(FirestoreConstants.RiskScoreFields.LEVEL)),
            factors = factors,
            insufficientData = getBoolean(FirestoreConstants.RiskScoreFields.INSUFFICIENT_DATA) ?: false,
            contributingReportIds = idsRaw.filterIsInstance<String>(),
            contributingIncidentCount = getLong(FirestoreConstants.RiskScoreFields.CONTRIBUTING_INCIDENT_COUNT)?.toInt() ?: 0,
            unresolvedCount = getLong(FirestoreConstants.RiskScoreFields.UNRESOLVED_COUNT)?.toInt() ?: 0,
            averageResolutionHours = getDouble(FirestoreConstants.RiskScoreFields.AVERAGE_RESOLUTION_HOURS) ?: 0.0,
            isRecurring = getBoolean(FirestoreConstants.RiskScoreFields.IS_RECURRING) ?: false,
            recommendation = getString(FirestoreConstants.RiskScoreFields.RECOMMENDATION).orEmpty(),
            history = history.sortedBy { it.timestamp },
            updatedAt = getLong(FirestoreConstants.RiskScoreFields.UPDATED_AT) ?: 0L,
        )
    }

    private fun RiskScore.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.RiskScoreFields.AREA_KEY to areaKey,
        FirestoreConstants.RiskScoreFields.AREA_LABEL to areaLabel,
        FirestoreConstants.RiskScoreFields.CATEGORY to category.name,
        FirestoreConstants.RiskScoreFields.CENTER_LATITUDE to centerLatitude,
        FirestoreConstants.RiskScoreFields.CENTER_LONGITUDE to centerLongitude,
        FirestoreConstants.RiskScoreFields.SCORE to score,
        FirestoreConstants.RiskScoreFields.LEVEL to level.name,
        FirestoreConstants.RiskScoreFields.FACTORS to factors.map { it.toFirestoreMap() },
        FirestoreConstants.RiskScoreFields.INSUFFICIENT_DATA to insufficientData,
        FirestoreConstants.RiskScoreFields.CONTRIBUTING_REPORT_IDS to contributingReportIds,
        FirestoreConstants.RiskScoreFields.CONTRIBUTING_INCIDENT_COUNT to contributingIncidentCount,
        FirestoreConstants.RiskScoreFields.UNRESOLVED_COUNT to unresolvedCount,
        FirestoreConstants.RiskScoreFields.AVERAGE_RESOLUTION_HOURS to averageResolutionHours,
        FirestoreConstants.RiskScoreFields.IS_RECURRING to isRecurring,
        FirestoreConstants.RiskScoreFields.RECOMMENDATION to recommendation,
        FirestoreConstants.RiskScoreFields.UPDATED_AT to updatedAt,
        // history intentionally omitted here - upsertRiskScore() appends it separately via
        // arrayUnion so this map's use with SetOptions.merge() never clobbers prior history.
    )

    private fun RiskFactor.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.RiskFactorFields.LABEL to label,
        FirestoreConstants.RiskFactorFields.POINTS to points,
        FirestoreConstants.RiskFactorFields.EXPLANATION to explanation,
    )

    private fun RiskHistoryPoint.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.RiskHistoryPointFields.SCORE to score,
        FirestoreConstants.RiskHistoryPointFields.TIMESTAMP to timestamp,
    )
}
