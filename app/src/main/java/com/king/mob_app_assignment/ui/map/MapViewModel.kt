package com.king.mob_app_assignment.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MapUiState(
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backs [MapScreen] - a community-wide view of every report as a pin, colored by status (the
 * same Pending/In Progress/Resolved convention as StatusBadge). Reads getAllReports() rather than
 * a per-user query since firestore.rules already makes /reports readable by any signed-in citizen
 * (the "All Reports" tab and this map both depend on that), and a pin with no location to plot
 * would be meaningless anyway - reports missing both latitude and longitude (the model's 0.0/0.0
 * default) are filtered out rather than shown stacked at the equator/prime-meridian intersection.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    reportRepository: ReportRepository,
) : ViewModel() {

    val uiState: StateFlow<MapUiState> = reportRepository.getAllReports()
        .map { reports ->
            MapUiState(
                reports = reports.filter { it.latitude != 0.0 || it.longitude != 0.0 },
                isLoading = false,
            )
        }
        .catch { emit(MapUiState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MapUiState(),
        )
}
