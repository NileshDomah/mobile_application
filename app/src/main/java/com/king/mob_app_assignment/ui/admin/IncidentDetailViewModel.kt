package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.repository.AuditLogRepository
import com.king.mob_app_assignment.data.repository.IncidentRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.data.repository.WorkOrderRepository
import com.king.mob_app_assignment.domain.model.AuditEntityType
import com.king.mob_app_assignment.domain.model.AuditLogEntry
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.IncidentStatus
import com.king.mob_app_assignment.domain.model.IncidentValidationState
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.WorkOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentDetailUiState(
    val incident: Incident? = null,
    val memberReports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
    // Reports matching the incident's category/area but NOT yet members - candidates a
    // human admin might want to manually merge in, surfaced via the same nearby-reports query
    // the auto-clustering worker uses, kept separate from the automatic path per the brief's
    // "allow admins to manually merge/unmerge" requirement.
    val mergeCandidates: List<Report> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList(),
    val auditHistory: List<AuditLogEntry> = emptyList(),
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    private val reportRepository: ReportRepository,
    private val workOrderRepository: WorkOrderRepository,
    private val auditLogRepository: AuditLogRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val incidentId: String = checkNotNull(savedStateHandle["incidentId"])

    private val incidentFlow = incidentRepository.getIncidentById(incidentId).catch { emit(null) }

    private val memberReportsFlow = incidentFlow.flatMapLatest { incident ->
        val memberIds = incident?.members?.map { it.reportId }?.toSet().orEmpty()
        reportRepository.getAllReports().map { all -> all.filter { it.id in memberIds } }
    }.catch { emit(emptyList()) }

    private val mergeCandidatesFlow = combine(incidentFlow, memberReportsFlow) { incident, members ->
        if (incident == null) return@combine emptyList()
        val memberIds = members.map { it.id }.toSet()
        runCatching {
            reportRepository.findNearbyRecentReports(
                category = incident.category,
                lat = incident.centerLatitude,
                lng = incident.centerLongitude,
                radiusMeters = 500.0,
                withinMinutes = 30L * 24 * 60,
            )
        }.getOrDefault(emptyList()).filter { it.id !in memberIds && it.incidentId != incident.id }
    }.catch { emit(emptyList()) }

    private val workOrdersFlow = workOrderRepository.getWorkOrdersForIncident(incidentId).catch { emit(emptyList()) }
    private val auditHistoryFlow = auditLogRepository.getEntriesForEntity(AuditEntityType.INCIDENT, incidentId).catch { emit(emptyList()) }

    val uiState: StateFlow<IncidentDetailUiState> = combine(
        incidentFlow, memberReportsFlow, mergeCandidatesFlow, workOrdersFlow, auditHistoryFlow,
    ) { incident, members, candidates, workOrders, auditHistory ->
        IncidentDetailUiState(
            incident = incident,
            memberReports = members.sortedByDescending { it.timestamp },
            mergeCandidates = candidates,
            workOrders = workOrders,
            auditHistory = auditHistory,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IncidentDetailUiState())

    fun mergeReport(reportId: String) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.manuallyMergeReport(incidentId, reportId, uid) }
    }

    fun unmergeReport(reportId: String) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.unmergeReport(incidentId, reportId, uid) }
    }

    fun updateStatus(status: IncidentStatus, note: String = "") {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.updateStatus(incidentId, status, note, uid) }
    }

    fun assignDepartment(department: Department?) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.assignDepartment(incidentId, department, uid) }
    }

    fun setSeverity(severity: ReportPriority) {
        viewModelScope.launch { incidentRepository.setSeverity(incidentId, severity) }
    }

    fun setValidationNote(note: String) {
        viewModelScope.launch { incidentRepository.setValidationNote(incidentId, note) }
    }

    fun validate(note: String) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.setValidationState(incidentId, IncidentValidationState.VALIDATED, note, uid) }
    }

    fun reject(note: String) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.setValidationState(incidentId, IncidentValidationState.REJECTED, note, uid) }
    }

    fun createWorkOrder(department: Department?, assignedTeam: String, priority: ReportPriority, dueDate: Long?) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch {
            workOrderRepository.createWorkOrder(incidentId, department, assignedTeam, priority, dueDate, uid)
        }
    }
}
