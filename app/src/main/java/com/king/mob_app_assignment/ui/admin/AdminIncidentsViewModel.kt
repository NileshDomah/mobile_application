package com.king.mob_app_assignment.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.repository.IncidentRepository
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.IncidentStatus
import com.king.mob_app_assignment.domain.model.ReportPriority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminIncidentsUiState(
    val incidents: List<Incident> = emptyList(),
    val isLoading: Boolean = true,
    val showClosedAndDismissed: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminIncidentsViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val showClosedFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val incidentsFlow = incidentRepository.getAllIncidents().catch { emit(emptyList()) }

    init {
        viewModelScope.launch {
            incidentRepository.incidentListErrors.collect { message -> errorFlow.value = message }
        }
    }

    val uiState: StateFlow<AdminIncidentsUiState> = combine(
        incidentsFlow, showClosedFlow, errorFlow,
    ) { incidents, showClosed, error ->
        AdminIncidentsUiState(
            incidents = incidents
                .filter { showClosed || it.status !in setOf(IncidentStatus.CLOSED, IncidentStatus.DISMISSED) }
                // Active/urgent incidents surface first - a command-center dashboard should not
                // make an admin scroll past closed history to find what needs attention now.
                .sortedWith(compareByDescending<Incident> { it.severity.ordinal }.thenByDescending { it.updatedAt }),
            isLoading = false,
            showClosedAndDismissed = showClosed,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminIncidentsUiState())

    fun onShowClosedToggle(value: Boolean) {
        showClosedFlow.value = value
    }

    fun dismissError() {
        errorFlow.value = null
    }

    fun updateStatus(incidentId: String, status: IncidentStatus, note: String = "") {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.updateStatus(incidentId, status, note, uid) }
    }

    fun assignDepartment(incidentId: String, department: Department?) {
        val uid = firebaseAuth.currentUser?.uid.orEmpty()
        viewModelScope.launch { incidentRepository.assignDepartment(incidentId, department, uid) }
    }

    fun setSeverity(incidentId: String, severity: ReportPriority) {
        viewModelScope.launch { incidentRepository.setSeverity(incidentId, severity) }
    }
}
