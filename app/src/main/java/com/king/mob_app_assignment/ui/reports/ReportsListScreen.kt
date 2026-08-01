package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsListScreen(
    title: String,
    scope: ReportsScope,
    onReportClick: (Report) -> Unit,
    viewModel: ReportsListViewModel = hiltViewModel(),
) {
    LaunchedEffect(scope) { viewModel.setScope(scope) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = title) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ReportFilterBar(filter = uiState.filter, onFilterChange = viewModel::onFilterChange)

            if (uiState.isLoading) {
                LoadingIndicator()
            } else if (uiState.reports.isEmpty()) {
                EmptyState(message = "No reports match your current filters yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportListItem(report = report, onClick = { onReportClick(report) })
                    }
                }
            }
        }
    }
}
