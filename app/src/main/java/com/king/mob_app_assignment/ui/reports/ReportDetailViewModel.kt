package com.king.mob_app_assignment.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Department
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
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository,
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
    )

    val uiState: StateFlow<ReportDetailUiState> = combine(
        reportRepository.getReportById(reportId).catch { emit(null) },
        isAdminFlow,
        currentUidFlow,
        editStateFlow,
    ) { report, isAdmin, currentUid, editState ->
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

    fun toggleUpvote() {
        val uid = uiState.value.currentUid ?: return
        viewModelScope.launch {
            reportRepository.toggleUpvote(reportId, uid)
        }
    }

    fun deleteReport() {
        viewModelScope.launch {
            val result = reportRepository.deleteReport(reportId)
            if (result.isSuccess) {
                editStateFlow.value = editStateFlow.value.copy(isDeleted = true)
            }
        }
    }
}
