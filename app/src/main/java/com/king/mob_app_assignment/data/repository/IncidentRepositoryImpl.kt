package com.king.mob_app_assignment.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.AuditEntityType
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.DepartmentSuggester
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.IncidentHistoryEntry
import com.king.mob_app_assignment.domain.model.IncidentMember
import com.king.mob_app_assignment.domain.model.IncidentMembershipSource
import com.king.mob_app_assignment.domain.model.IncidentStatus
import com.king.mob_app_assignment.domain.model.IncidentValidationState
import com.king.mob_app_assignment.domain.model.PriorityFactor
import com.king.mob_app_assignment.domain.model.PriorityScorer
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.util.CrashReporter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Clustering thresholds - all named constants (not scattered magic numbers) so the "why 65%, not
// 50%" question has one visible answer, and so a future ML model replacing this rule-based score
// has a clear target to beat, not an implicit one buried in comparisons. See
// REPORT_NOTES.md/PLACEHOLDERS_TODO.csv for the full rationale on why these specific values were
// chosen for a rule-based v1 rather than tuned against real labeled data (none exists yet).
private const val AUTO_MERGE_CONFIDENCE_THRESHOLD = 0.65f
private const val LOW_CONFIDENCE_FLOOR = 0.35f
private const val MIN_REPORTS_TO_PROPOSE_NEW_INCIDENT = 3
private const val CLUSTER_SEARCH_RADIUS_METERS = 250.0
private const val CLUSTER_SEARCH_WINDOW_MINUTES = 14L * 24 * 60 // 14 days - incidents are a
// slower-moving concept than the existing 10-minute duplicate-submission check reuses the same
// haversine/whereEqualTo(category) query shape for, just over a much longer window.

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val reportRepository: ReportRepository,
    private val auditLogRepository: AuditLogRepository,
) : IncidentRepository {

    private val incidentsCollection = firestore.collection(FirestoreConstants.INCIDENTS_COLLECTION)

    private val _incidentListErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val incidentListErrors: Flow<String> = _incidentListErrors.asSharedFlow()
    private var lastReportedErrorMessage: String? = null

    private fun listenerError(source: String, error: Exception) {
        CrashReporter.log("IncidentRepositoryImpl.$source: snapshot listener error")
        CrashReporter.recordException(error)
        val message = "Couldn't load incidents (${error.message ?: "unknown error"})"
        if (message != lastReportedErrorMessage) {
            lastReportedErrorMessage = message
            _incidentListErrors.tryEmit(message)
        }
    }

    override fun getAllIncidents(): Flow<List<Incident>> = callbackFlow {
        val listener = incidentsCollection
            .orderBy(FirestoreConstants.IncidentFields.UPDATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    listenerError("getAllIncidents", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toIncidentOrNull() })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getIncidentById(id: String): Flow<Incident?> = callbackFlow {
        val listener = incidentsCollection.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                listenerError("getIncidentById", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.toIncidentOrNull())
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getIncidentForReport(incidentId: String): Flow<Incident?> = getIncidentById(incidentId)

    override suspend fun evaluateAndClusterReport(report: Report): ClusteringOutcome {
        if (report.incidentId.isNotBlank()) {
            // Already clustered (e.g. re-run on a report that was already processed) - nothing
            // to do. Guards against the worker double-processing on retry.
            return ClusteringOutcome.NoMatch
        }

        val nearby = runCatching {
            reportRepository.findNearbyRecentReports(
                category = report.category,
                lat = report.latitude,
                lng = report.longitude,
                radiusMeters = CLUSTER_SEARCH_RADIUS_METERS,
                withinMinutes = CLUSTER_SEARCH_WINDOW_MINUTES,
            )
        }.getOrElse {
            CrashReporter.log("IncidentRepositoryImpl.evaluateAndClusterReport: nearby-report query failed")
            CrashReporter.recordException(it)
            return ClusteringOutcome.NoMatch
        }.filter { it.id != report.id }

        if (nearby.isEmpty()) return ClusteringOutcome.NoMatch

        // Prefer attaching to an existing OPEN incident already covering this area/category over
        // proposing a brand new one - directly satisfies "prevent duplicate incidents from being
        // created when a suitable active incident already exists". Reports already in an
        // incident are the strongest signal of "there's already a home for this".
        // Score EVERY open nearby incident before deciding anything - a report can be near
        // several existing incidents at once (e.g. a weak 0.40 match encountered first, a strong
        // 0.80 match encountered later), and only ever settling for the first one crossing either
        // threshold could attach a report to the wrong incident, or strand it as a low-confidence
        // candidate on a weak match while a genuinely strong match sits unexamined later in the
        // list. Picking the single best-scoring candidate across all of them is what "attach to
        // the existing incident it actually belongs to" requires.
        val existingIncidentIds = nearby.mapNotNull { it.incidentId.takeIf(String::isNotBlank) }.distinct()
        val scoredCandidates = existingIncidentIds.mapNotNull { incidentId ->
            val incident = runCatching { incidentsCollection.document(incidentId).get().await().toIncidentOrNull() }
                .getOrNull() ?: return@mapNotNull null
            if (incident.status == IncidentStatus.CLOSED || incident.status == IncidentStatus.DISMISSED) return@mapNotNull null
            incident to confidenceForAttachingTo(report, incident, nearby)
        }
        val bestCandidate = scoredCandidates.maxByOrNull { (_, confidence) -> confidence }
        if (bestCandidate != null) {
            val (incident, confidence) = bestCandidate
            when {
                confidence >= AUTO_MERGE_CONFIDENCE_THRESHOLD -> {
                    attachReportToIncident(incident, report, confidence)
                    return ClusteringOutcome.AttachedToExisting(incident.id, confidence)
                }
                confidence >= LOW_CONFIDENCE_FLOOR ->
                    return ClusteringOutcome.LowConfidenceCandidate(incident.id, confidence)
                else -> Unit
            }
        }

        // No existing incident to attach to - consider proposing a brand new one, but only once
        // there's a real cluster (this report plus enough OTHER unclustered nearby reports), per
        // the brief's explicit "do not automatically merge reports if confidence is low"
        // requirement - a single report with one neighbor is not yet "17 citizens reported the
        // same pothole," it's plausibly still two unrelated reports.
        val unclusteredNearby = nearby.filter { it.incidentId.isBlank() }
        val candidateGroup = unclusteredNearby + report
        if (candidateGroup.size < MIN_REPORTS_TO_PROPOSE_NEW_INCIDENT) return ClusteringOutcome.NoMatch

        val confidence = confidenceForNewCluster(candidateGroup)
        if (confidence < AUTO_MERGE_CONFIDENCE_THRESHOLD) {
            // Not confident enough to auto-create - still worth flagging so an admin can look at
            // it, but there's no incident to point LowConfidenceCandidate at yet since none was
            // created. Treated as NoMatch for now; the admin-triggered manual "propose incident
            // from these reports" action (AdminIncidentsScreen) is the fallback for this case -
            // see PLACEHOLDERS_TODO.csv for the acknowledged gap (no low-confidence NEW-cluster
            // surfacing yet, only low-confidence EXISTING-incident surfacing).
            return ClusteringOutcome.NoMatch
        }

        val incidentId = createIncidentFromCluster(candidateGroup, confidence)
        return ClusteringOutcome.CreatedNew(incidentId, candidateGroup.map { it.id })
    }

    /**
     * Rule-based confidence score (0f-1f) that [report] belongs to the same underlying problem
     * as [incident]'s existing members. Deliberately simple, transparent, and rule-based per the
     * brief's explicit instruction not to pretend at ML sophistication the app doesn't have -
     * every factor here is independently inspectable and the weights sum to 1.0, so an admin (or
     * a future ML model swapped in to replace this function) has a clear, auditable baseline to
     * compare against.
     *
     * Factors (brief's required list): geographic distance, category, time proximity, AI
     * classification/severity. Visual similarity is NOT implemented (see
     * PLACEHOLDERS_TODO.csv) - it would require embedding/comparing photos, which needs a real
     * vision-similarity model this project's LAN-only classifier doesn't provide; the weight
     * that would have gone to it is folded into category+distance instead. Upvotes-as-community-
     * confirmation was removed entirely along with the rest of the upvoting feature (see
     * PLACEHOLDERS_TODO.csv) - the 0.10 weight it used to carry was reassigned to distance
     * (0.30 -> 0.40), the single strongest geographic signal, rather than left unspent.
     */
    private fun confidenceForAttachingTo(report: Report, incident: Incident, nearby: List<Report>): Float {
        var score = 0f

        // Category match is close to a hard requirement - the brief's own example clusters
        // "potholes," not mixed issue types - but stays a scored factor rather than a filter so
        // a CUSTOM-labeled report describing the same real-world problem in different words
        // isn't automatically excluded.
        val categoryMatches = report.category == incident.category &&
            (report.category != ReportCategory.CUSTOM || report.customLabel.equalsIgnoreCaseTrim(incident.customLabel))
        score += if (categoryMatches) 0.35f else 0f

        val distanceMeters = haversineMeters(report.latitude, report.longitude, incident.centerLatitude, incident.centerLongitude)
        score += 0.40f * proximityFactor(distanceMeters)

        val incidentAgeMinutes = (System.currentTimeMillis() - incident.createdAt) / 60_000.0
        score += 0.15f * recencyFactor(incidentAgeMinutes)

        // Severity agreement - a report the AI/admin marked URGENT joining an incident already
        // flagged HIGH/URGENT reinforces "same real problem, and it's serious," vs. a LOW-
        // severity report attaching to an otherwise-URGENT incident being a weaker signal.
        score += if (severityAgrees(report.priority, incident.severity)) 0.10f else 0f

        return score.coerceIn(0f, 1f)
    }

    /**
     * Same factor shape as [confidenceForAttachingTo], but for a not-yet-created cluster of
     * reports. The report-count weight below absorbed the 0.10 that upvotes used to carry here
     * (0.10 -> 0.20) when upvoting was removed from the app - see PLACEHOLDERS_TODO.csv.
     */
    private fun confidenceForNewCluster(reports: List<Report>): Float {
        val allSameCategory = reports.map { it.category }.distinct().size == 1
        var score = if (allSameCategory) 0.35f else 0.1f

        val centerLat = reports.map { it.latitude }.average()
        val centerLng = reports.map { it.longitude }.average()
        val avgDistance = reports.map { haversineMeters(it.latitude, it.longitude, centerLat, centerLng) }.average()
        score += 0.30f * proximityFactor(avgDistance)

        val timestamps = reports.map { it.timestamp }
        val spanMinutes = ((timestamps.max() - timestamps.min()) / 60_000.0)
        score += 0.15f * recencyFactor(spanMinutes)

        // More independent reports of the same thing is itself a confidence signal for a NEW
        // cluster specifically (there's no existing incident's track record to lean on yet, so
        // the reports' own count has to carry more weight here than it does when attaching to an
        // already-established incident above).
        score += 0.20f * min(1f, (reports.size - MIN_REPORTS_TO_PROPOSE_NEW_INCIDENT) / 5f + 0.5f)

        return score.coerceIn(0f, 1f)
    }

    // Linear falloff: full credit at 0m, zero credit at/beyond CLUSTER_SEARCH_RADIUS_METERS.
    // findNearbyRecentReports() already hard-filters to within that radius, so this only ever
    // discriminates within the window, not gates it - the gating already happened at the query.
    private fun proximityFactor(distanceMeters: Double): Float =
        (1.0 - (distanceMeters / CLUSTER_SEARCH_RADIUS_METERS)).coerceIn(0.0, 1.0).toFloat()

    // Linear falloff over the search window, same reasoning as proximityFactor.
    private fun recencyFactor(minutesElapsed: Double): Float =
        (1.0 - (minutesElapsed / CLUSTER_SEARCH_WINDOW_MINUTES)).coerceIn(0.0, 1.0).toFloat()

    private fun severityAgrees(a: ReportPriority, b: ReportPriority): Boolean {
        val highTier = setOf(ReportPriority.HIGH, ReportPriority.URGENT)
        return a == b || (a in highTier && b in highTier)
    }

    private fun String.equalsIgnoreCaseTrim(other: String): Boolean = this.trim().equals(other.trim(), ignoreCase = true)

    private suspend fun attachReportToIncident(incident: Incident, report: Report, confidence: Float) {
        val member = IncidentMember(
            reportId = report.id,
            source = IncidentMembershipSource.AUTO_CLUSTERED,
            confidence = confidence,
        )
        runCatching {
            incidentsCollection.document(incident.id).update(
                mapOf(
                    FirestoreConstants.IncidentFields.MEMBERS to FieldValue.arrayUnion(member.toMap()),
                    FirestoreConstants.IncidentFields.REPORT_COUNT to incident.reportCount + 1,
                    FirestoreConstants.IncidentFields.UPDATED_AT to System.currentTimeMillis(),
                ),
            ).await()
            reportRepository.setReportIncidentId(report.id, incident.id)
        }.onFailure {
            CrashReporter.log("IncidentRepositoryImpl.attachReportToIncident: failed for incident=${incident.id} report=${report.id}")
            CrashReporter.recordException(it)
        }
    }

    private suspend fun createIncidentFromCluster(reports: List<Report>, confidence: Float): String {
        val docRef = incidentsCollection.document()
        // Previously picked the most-upvoted report as the representative one (title/category
        // source) - upvoting was removed from the app, so this now just takes the earliest
        // report, the one that actually first identified the problem.
        val representative = reports.minByOrNull { it.timestamp } ?: reports.first()
        val centerLat = reports.map { it.latitude }.average()
        val centerLng = reports.map { it.longitude }.average()
        val severity = reports.map { it.priority }.maxByOrNull { it.ordinal } ?: ReportPriority.NORMAL

        var incident = Incident(
            id = docRef.id,
            title = incidentTitleFor(representative),
            category = representative.category,
            customLabel = representative.customLabel,
            status = IncidentStatus.DETECTED,
            severity = severity,
            department = DepartmentSuggester.suggestFor(representative.category, representative.customLabel),
            centerLatitude = centerLat,
            centerLongitude = centerLng,
            address = representative.address,
            reportCount = reports.size,
            members = reports.map { report ->
                IncidentMember(reportId = report.id, source = IncidentMembershipSource.AUTO_CLUSTERED, confidence = confidence)
            },
            history = listOf(IncidentHistoryEntry(status = IncidentStatus.DETECTED, note = "Auto-detected from ${reports.size} related reports")),
        )
        val factors = PriorityScorer.score(incident, incident.reportCount)
        incident = incident.copy(priorityFactors = factors, priorityScore = PriorityScorer.totalScore(factors))

        runCatching {
            docRef.set(incident.toFirestoreMap()).await()
            reports.forEach { reportRepository.setReportIncidentId(it.id, docRef.id) }
        }.onFailure {
            CrashReporter.log("IncidentRepositoryImpl.createIncidentFromCluster: write failed")
            CrashReporter.recordException(it)
        }
        return docRef.id
    }

    private fun incidentTitleFor(report: Report): String {
        val label = if (report.category == ReportCategory.CUSTOM && report.customLabel.isNotBlank()) {
            report.customLabel
        } else {
            report.category.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
        val location = report.address.ifBlank { "Unknown location" }.substringBefore(",").trim()
        return "$label - $location"
    }

    override suspend fun manuallyMergeReport(incidentId: String, reportId: String, performedByUid: String): Result<Unit> = runCatching {
        val incidentSnapshot = incidentsCollection.document(incidentId).get().await()
        val incident = incidentSnapshot.toIncidentOrNull() ?: error("Incident $incidentId not found")

        val member = IncidentMember(reportId = reportId, source = IncidentMembershipSource.ADMIN_MERGED, confidence = 1f)
        incidentsCollection.document(incidentId).update(
            mapOf(
                FirestoreConstants.IncidentFields.MEMBERS to FieldValue.arrayUnion(member.toMap()),
                FirestoreConstants.IncidentFields.REPORT_COUNT to incident.reportCount + 1,
                FirestoreConstants.IncidentFields.UPDATED_AT to System.currentTimeMillis(),
                FirestoreConstants.IncidentFields.HISTORY to FieldValue.arrayUnion(
                    IncidentHistoryEntry(status = incident.status, note = "Report manually merged", performedByUid = performedByUid).toMap(),
                ),
            ),
        ).await()
        reportRepository.setReportIncidentId(reportId, incidentId)
        Unit
    }

    override suspend fun unmergeReport(incidentId: String, reportId: String, performedByUid: String): Result<Unit> = runCatching {
        val incidentSnapshot = incidentsCollection.document(incidentId).get().await()
        val incident = incidentSnapshot.toIncidentOrNull() ?: error("Incident $incidentId not found")

        val remainingMembers = incident.members.filterNot { it.reportId == reportId }
        incidentsCollection.document(incidentId).update(
            mapOf(
                FirestoreConstants.IncidentFields.MEMBERS to remainingMembers.map { it.toMap() },
                FirestoreConstants.IncidentFields.REPORT_COUNT to remainingMembers.size,
                FirestoreConstants.IncidentFields.UPDATED_AT to System.currentTimeMillis(),
                FirestoreConstants.IncidentFields.HISTORY to FieldValue.arrayUnion(
                    IncidentHistoryEntry(status = incident.status, note = "Report unmerged", performedByUid = performedByUid).toMap(),
                ),
            ),
        ).await()
        reportRepository.setReportIncidentId(reportId, "")
        Unit
    }

    override suspend fun updateStatus(incidentId: String, status: IncidentStatus, note: String, performedByUid: String): Result<Unit> = runCatching {
        val previous = incidentsCollection.document(incidentId).get().await().toIncidentOrNull()?.status?.name.orEmpty()
        incidentsCollection.document(incidentId).update(
            mapOf(
                FirestoreConstants.IncidentFields.STATUS to status.name,
                FirestoreConstants.IncidentFields.UPDATED_AT to System.currentTimeMillis(),
                FirestoreConstants.IncidentFields.HISTORY to FieldValue.arrayUnion(
                    IncidentHistoryEntry(status = status, note = note, performedByUid = performedByUid).toMap(),
                ),
            ),
        ).await()
        auditLogRepository.record(
            entityType = AuditEntityType.INCIDENT,
            entityId = incidentId,
            action = "STATUS_CHANGED",
            performedByUid = performedByUid,
            previousValue = previous,
            newValue = status.name,
            note = note,
        )
        recomputePriorityScore(incidentId)
        Unit
    }

    override suspend fun assignDepartment(incidentId: String, department: Department?, performedByUid: String): Result<Unit> = runCatching {
        val previous = incidentsCollection.document(incidentId).get().await().toIncidentOrNull()?.department?.name.orEmpty()
        incidentsCollection.document(incidentId)
            .update(FirestoreConstants.IncidentFields.DEPARTMENT, department?.name)
            .await()
        auditLogRepository.record(
            entityType = AuditEntityType.INCIDENT,
            entityId = incidentId,
            action = "DEPARTMENT_ASSIGNED",
            performedByUid = performedByUid,
            previousValue = previous,
            newValue = department?.name.orEmpty(),
        )
        Unit
    }

    override suspend fun setSeverity(incidentId: String, severity: ReportPriority): Result<Unit> = runCatching {
        incidentsCollection.document(incidentId)
            .update(FirestoreConstants.IncidentFields.SEVERITY, severity.name)
            .await()
        recomputePriorityScore(incidentId)
        Unit
    }

    override suspend fun setValidationNote(incidentId: String, note: String): Result<Unit> = runCatching {
        incidentsCollection.document(incidentId)
            .update(FirestoreConstants.IncidentFields.VALIDATION_NOTE, note)
            .await()
        Unit
    }

    override suspend fun setValidationState(
        incidentId: String,
        state: IncidentValidationState,
        note: String,
        performedByUid: String,
    ): Result<Unit> = runCatching {
        incidentsCollection.document(incidentId).update(
            mapOf(
                FirestoreConstants.IncidentFields.VALIDATION_STATE to state.name,
                FirestoreConstants.IncidentFields.VALIDATION_NOTE to note,
            ),
        ).await()
        auditLogRepository.record(
            entityType = AuditEntityType.INCIDENT,
            entityId = incidentId,
            action = "VALIDATION_${state.name}",
            performedByUid = performedByUid,
            note = note,
        )
        Unit
    }

    override suspend fun recomputePriorityScore(incidentId: String): Result<Unit> = runCatching {
        val incident = incidentsCollection.document(incidentId).get().await().toIncidentOrNull() ?: return@runCatching Unit
        val factors = PriorityScorer.score(incident, incident.reportCount)
        incidentsCollection.document(incidentId).update(
            mapOf(
                FirestoreConstants.IncidentFields.PRIORITY_SCORE to PriorityScorer.totalScore(factors),
                FirestoreConstants.IncidentFields.PRIORITY_FACTORS to factors.map { it.toMap() },
            ),
        ).await()
        Unit
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

    private fun IncidentMember.toMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.IncidentMemberFields.REPORT_ID to reportId,
        FirestoreConstants.IncidentMemberFields.SOURCE to source.name,
        FirestoreConstants.IncidentMemberFields.CONFIDENCE to confidence,
        FirestoreConstants.IncidentMemberFields.ADDED_AT to addedAt,
    )

    private fun IncidentHistoryEntry.toMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.IncidentHistoryEntryFields.STATUS to status.name,
        FirestoreConstants.IncidentHistoryEntryFields.TIMESTAMP to timestamp,
        FirestoreConstants.IncidentHistoryEntryFields.NOTE to note,
        FirestoreConstants.IncidentHistoryEntryFields.PERFORMED_BY_UID to performedByUid,
    )

    private fun PriorityFactor.toMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.PriorityFactorFields.LABEL to label,
        FirestoreConstants.PriorityFactorFields.POINTS to points,
        FirestoreConstants.PriorityFactorFields.EXPLANATION to explanation,
    )

    private fun DocumentSnapshot.toIncidentOrNull(): Incident? {
        if (!exists()) return null
        return Incident(
            id = id,
            title = getString(FirestoreConstants.IncidentFields.TITLE).orEmpty(),
            category = ReportCategory.fromFirestoreValue(getString(FirestoreConstants.IncidentFields.CATEGORY)),
            customLabel = getString(FirestoreConstants.IncidentFields.CUSTOM_LABEL).orEmpty(),
            status = IncidentStatus.fromFirestoreValue(getString(FirestoreConstants.IncidentFields.STATUS)),
            severity = ReportPriority.fromFirestoreValue(getString(FirestoreConstants.IncidentFields.SEVERITY)),
            department = Department.fromFirestoreValue(getString(FirestoreConstants.IncidentFields.DEPARTMENT)),
            centerLatitude = getDouble(FirestoreConstants.IncidentFields.CENTER_LATITUDE) ?: 0.0,
            centerLongitude = getDouble(FirestoreConstants.IncidentFields.CENTER_LONGITUDE) ?: 0.0,
            address = getString(FirestoreConstants.IncidentFields.ADDRESS).orEmpty(),
            reportCount = (getLong(FirestoreConstants.IncidentFields.REPORT_COUNT) ?: 0L).toInt(),
            members = (get(FirestoreConstants.IncidentFields.MEMBERS) as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                IncidentMember(
                    reportId = (map[FirestoreConstants.IncidentMemberFields.REPORT_ID] as? String).orEmpty(),
                    source = IncidentMembershipSource.entries.firstOrNull {
                        it.name == map[FirestoreConstants.IncidentMemberFields.SOURCE] as? String
                    } ?: IncidentMembershipSource.AUTO_CLUSTERED,
                    confidence = (map[FirestoreConstants.IncidentMemberFields.CONFIDENCE] as? Double)?.toFloat() ?: 1f,
                    addedAt = (map[FirestoreConstants.IncidentMemberFields.ADDED_AT] as? Long) ?: 0L,
                )
            } ?: emptyList(),
            history = (get(FirestoreConstants.IncidentFields.HISTORY) as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                IncidentHistoryEntry(
                    status = IncidentStatus.fromFirestoreValue(map[FirestoreConstants.IncidentHistoryEntryFields.STATUS] as? String),
                    timestamp = (map[FirestoreConstants.IncidentHistoryEntryFields.TIMESTAMP] as? Long) ?: 0L,
                    note = (map[FirestoreConstants.IncidentHistoryEntryFields.NOTE] as? String).orEmpty(),
                    performedByUid = (map[FirestoreConstants.IncidentHistoryEntryFields.PERFORMED_BY_UID] as? String).orEmpty(),
                )
            }?.sortedBy { it.timestamp } ?: emptyList(),
            createdAt = getLong(FirestoreConstants.IncidentFields.CREATED_AT) ?: 0L,
            updatedAt = getLong(FirestoreConstants.IncidentFields.UPDATED_AT) ?: 0L,
            validationNote = getString(FirestoreConstants.IncidentFields.VALIDATION_NOTE).orEmpty(),
            validationState = IncidentValidationState.entries.firstOrNull {
                it.name == getString(FirestoreConstants.IncidentFields.VALIDATION_STATE)
            } ?: IncidentValidationState.PENDING,
            priorityScore = (getLong(FirestoreConstants.IncidentFields.PRIORITY_SCORE) ?: 0L).toInt(),
            priorityFactors = (get(FirestoreConstants.IncidentFields.PRIORITY_FACTORS) as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                PriorityFactor(
                    label = (map[FirestoreConstants.PriorityFactorFields.LABEL] as? String).orEmpty(),
                    points = (map[FirestoreConstants.PriorityFactorFields.POINTS] as? Long)?.toInt() ?: 0,
                    explanation = (map[FirestoreConstants.PriorityFactorFields.EXPLANATION] as? String).orEmpty(),
                )
            } ?: emptyList(),
        )
    }

    private fun Incident.toFirestoreMap(): Map<String, Any?> = mapOf(
        FirestoreConstants.IncidentFields.TITLE to title,
        FirestoreConstants.IncidentFields.CATEGORY to category.name,
        FirestoreConstants.IncidentFields.CUSTOM_LABEL to customLabel,
        FirestoreConstants.IncidentFields.STATUS to status.name,
        FirestoreConstants.IncidentFields.SEVERITY to severity.name,
        FirestoreConstants.IncidentFields.DEPARTMENT to department?.name,
        FirestoreConstants.IncidentFields.CENTER_LATITUDE to centerLatitude,
        FirestoreConstants.IncidentFields.CENTER_LONGITUDE to centerLongitude,
        FirestoreConstants.IncidentFields.ADDRESS to address,
        FirestoreConstants.IncidentFields.REPORT_COUNT to reportCount,
        FirestoreConstants.IncidentFields.MEMBERS to members.map { it.toMap() },
        FirestoreConstants.IncidentFields.HISTORY to history.map { it.toMap() },
        FirestoreConstants.IncidentFields.CREATED_AT to createdAt,
        FirestoreConstants.IncidentFields.UPDATED_AT to updatedAt,
        FirestoreConstants.IncidentFields.VALIDATION_NOTE to validationNote,
        FirestoreConstants.IncidentFields.VALIDATION_STATE to validationState.name,
        FirestoreConstants.IncidentFields.PRIORITY_SCORE to priorityScore,
        FirestoreConstants.IncidentFields.PRIORITY_FACTORS to priorityFactors.map { it.toMap() },
    )
}
