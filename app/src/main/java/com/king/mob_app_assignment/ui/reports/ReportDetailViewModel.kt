package com.king.mob_app_assignment.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.IncidentRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReportDetailUiState(
    val report: Report? = null,
    val isAdmin: Boolean = false,
    val isOwner: Boolean = false,
    val currentUid: String? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleted: Boolean = false,
    val isUpdatingStatus: Boolean = false,
    val deleteErrorMessage: String? = null,
    // Non-null only when this report has been clustered into an incident - citizens see a small
    // "this is part of a wider tracked issue" card, admins get a link into the full incident
    // detail. Most reports most of the time have no incident, so this staying null is the common
    // case, not an error state.
    val incident: Incident? = null,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository,
    private val incidentRepository: IncidentRepository,
) : ViewModel() {

    private val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val isAdminFlow = authRepository.currentUser
        .map { it?.role == UserRole.ADMIN }
        .catch { emit(false) }

    private val currentUidFlow = authRepository.currentUser
        .map { it?.uid }
        .catch { emit(null) }

    private val editStateFlow = MutableStateFlow(EditState())

    private data class EditState(
        val isEditing: Boolean = false,
        val isSaving: Boolean = false,
        val isDeleted: Boolean = false,
        val isUpdatingStatus: Boolean = false,
        val deleteErrorMessage: String? = null,
    )

    private val reportFlow = reportRepository.getReportById(reportId).catch { emit(null) }

    private val incidentFlow = reportFlow.flatMapLatest { report ->
        val incidentId = report?.incidentId.orEmpty()
        if (incidentId.isBlank()) flowOf(null) else incidentRepository.getIncidentById(incidentId)
    }.catch { emit(null) }

    val uiState: StateFlow<ReportDetailUiState> = combine(
        reportFlow,
        isAdminFlow,
        currentUidFlow,
        editStateFlow,
        incidentFlow,
    ) { report, isAdmin, currentUid, editState, incident ->
        ReportDetailUiState(
            report = report,
            isAdmin = isAdmin,
            isOwner = report != null && currentUid != null && report.userId == currentUid,
            currentUid = currentUid,
            isLoading = false,
            isEditing = editState.isEditing,
            isSaving = editState.isSaving,
            isDeleted = editState.isDeleted,
            isUpdatingStatus = editState.isUpdatingStatus,
            deleteErrorMessage = editState.deleteErrorMessage,
            incident = incident,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportDetailUiState())

    fun updateStatus(status: ReportStatus) {
        viewModelScope.launch {
            reportRepository.updateReportStatus(reportId, status)
        }
    }

    fun assignDepartment(department: Department?) {
        viewModelScope.launch {
            reportRepository.assignDepartment(reportId, department)
        }
    }

    fun setPriority(priority: ReportPriority) {
        viewModelScope.launch {
            reportRepository.setPriority(reportId, priority)
        }
    }

    fun updateStatusWithNote(status: ReportStatus, note: String, photoFile: File?) {
        viewModelScope.launch {
            editStateFlow.value = editStateFlow.value.copy(isUpdatingStatus = true)
            reportRepository.resolveWithNote(reportId, status, note, photoFile)
            editStateFlow.value = editStateFlow.value.copy(isUpdatingStatus = false)
        }
    }

    fun startEditing() {
        editStateFlow.value = editStateFlow.value.copy(isEditing = true)
    }

    fun cancelEditing() {
        editStateFlow.value = editStateFlow.value.copy(isEditing = false)
    }

    fun saveEdit(category: ReportCategory, note: String, customLabel: String) {
        viewModelScope.launch {
            editStateFlow.value = editStateFlow.value.copy(isSaving = true)
            val needsAiReview = uiState.value.report?.needsAiReview == true
            val result = if (needsAiReview) {
                reportRepository.resubmitAfterAiReview(reportId, category, note, customLabel)
            } else {
                reportRepository.updateReport(reportId, category, note, customLabel)
            }
            editStateFlow.value = editStateFlow.value.copy(
                isSaving = false,
                isEditing = result.isFailure,
            )
        }
    }

    fun deleteReport() {
        viewModelScope.launch {
            val result = reportRepository.deleteReport(reportId)
            editStateFlow.value = if (result.isSuccess) {
                editStateFlow.value.copy(isDeleted = true, deleteErrorMessage = null)
            } else {
                // Previously swallowed silently on failure - the dialog would close and the
                // screen would just redraw with the report still present, which looked like
                // "it disappears then reappears" with zero explanation of why. Now surfaces the
                // actual failure reason (most likely: Firestore rules rejected it - e.g. the
                // report is no longer SUBMITTED, or the rules deployed to the real project are
                // stale relative to this build - or a genuine network error).
                editStateFlow.value.copy(
                    deleteErrorMessage = result.exceptionOrNull()?.message ?: "Delete failed - please try again",
                )
            }
        }
    }

    fun dismissDeleteError() {
        editStateFlow.value = editStateFlow.value.copy(deleteErrorMessage = null)
    }
}
