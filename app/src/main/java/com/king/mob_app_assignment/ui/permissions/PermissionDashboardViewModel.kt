package com.king.mob_app_assignment.ui.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.king.mob_app_assignment.util.AppPermission
import com.king.mob_app_assignment.util.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionStatusUi(
    val permission: AppPermission,
    val isGranted: Boolean,
)

data class PermissionDashboardUiState(
    val statuses: List<PermissionStatusUi> = emptyList(),
)

class PermissionDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PermissionDashboardUiState())
    val uiState: StateFlow<PermissionDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        _uiState.value = PermissionDashboardUiState(
            statuses = AppPermission.entries.map { permission ->
                PermissionStatusUi(
                    permission = permission,
                    isGranted = PermissionManager.isGranted(context, permission),
                )
            },
        )
    }
}
