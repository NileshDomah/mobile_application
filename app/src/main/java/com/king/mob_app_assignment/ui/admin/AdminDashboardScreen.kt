package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.ReportListItem
import com.king.mob_app_assignment.ui.reports.displayLabel as categoryDisplayLabel

@Composable
fun AdminDashboardScreen(
    onReportClick: (Report) -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Admin dashboard") },
    ) { padding ->
        if (uiState.isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatsSummaryRow(uiState)

            StatBreakdownCard(
                title = "By status",
                entries = ReportStatus.entries.map { it.label() to (uiState.countByStatus[it] ?: 0) },
            )

            if (uiState.countByCategory.isNotEmpty()) {
                StatBreakdownCard(
                    title = "By category",
                    entries = uiState.countByCategory.entries
                        .sortedByDescending { it.value }
                        .map { it.key.categoryDisplayLabel() to it.value },
                )
            }

            StatBreakdownCard(
                title = "By department",
                entries = uiState.countByDepartment.entries
                    .sortedByDescending { it.value }
                    .map { (it.key?.displayLabel() ?: "Unassigned") to it.value },
            )

            Text(
                text = "Needs attention",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Unassigned, urgent, or AI-flagged reports that aren't resolved yet - the reports most likely to need your action next.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.needsAttention.isEmpty()) {
                EmptyState(message = "Nothing needs attention right now.")
            } else {
                // Nested LazyColumn inside a scrollable Column - fine at this list's small,
                // capped size (NEEDS_ATTENTION_LIMIT = 20), not intended as a paginated feed.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(uiState.needsAttention, key = { it.id }) { report ->
                        ReportListItem(report = report, onClick = { onReportClick(report) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSummaryRow(uiState: AdminDashboardUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SummaryStatCard(label = "Total reports", value = uiState.totalReports.toString(), modifier = Modifier.weight(1f))
        SummaryStatCard(
            label = "Awaiting action",
            value = ((uiState.countByStatus[ReportStatus.SUBMITTED] ?: 0) + (uiState.countByStatus[ReportStatus.IN_REVIEW] ?: 0)).toString(),
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(label = "Citizen upvotes", value = uiState.totalUpvotes.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StatBreakdownCard(title: String, entries: List<Pair<String, Int>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            entries.forEach { (label, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> "Submitted"
    ReportStatus.IN_REVIEW -> "In review"
    ReportStatus.RESOLVED -> "Resolved"
}
