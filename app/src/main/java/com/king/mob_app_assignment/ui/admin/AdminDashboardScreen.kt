package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.ReportListItem
import com.king.mob_app_assignment.ui.reports.displayLabel as categoryDisplayLabel
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
fun AdminDashboardScreen(
    onReportClick: (Report) -> Unit,
    onIncidentClick: (com.king.mob_app_assignment.domain.model.Incident) -> Unit = {},
    onViewAllIncidents: () -> Unit = {},
    onViewRiskAreas: () -> Unit = {},
    onPromoteAdminClick: () -> Unit = {},
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.admin_dashboard_title)) },
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                com.king.mob_app_assignment.ui.components.NavigationRow(
                    label = translatedStringResource(R.string.promote_admin_nav_label),
                    icon = Icons.Filled.AdminPanelSettings,
                    onClick = onPromoteAdminClick,
                )
            }

            StatBreakdownCard(
                title = translatedStringResource(R.string.admin_dashboard_by_status),
                entries = ReportStatus.entries.map { it.label() to (uiState.countByStatus[it] ?: 0) },
            )

            if (uiState.countByCategory.isNotEmpty()) {
                StatBreakdownCard(
                    title = translatedStringResource(R.string.admin_dashboard_by_category),
                    entries = uiState.countByCategory.entries
                        .sortedByDescending { it.value }
                        .map { it.key.categoryDisplayLabel() to it.value },
                )
            }

            val unassignedLabel = translatedStringResource(R.string.admin_dashboard_unassigned)
            StatBreakdownCard(
                title = translatedStringResource(R.string.admin_dashboard_by_department),
                entries = uiState.countByDepartment.entries
                    .sortedByDescending { it.value }
                    .map { (it.key?.displayLabel() ?: unassignedLabel) to it.value },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(text = translatedStringResource(R.string.admin_dashboard_active_incidents, uiState.activeIncidentCount), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onViewAllIncidents) { Text(translatedStringResource(R.string.admin_dashboard_view_all)) }
            }
            Text(
                text = translatedStringResource(R.string.admin_dashboard_active_incidents_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.activeIncidents.isEmpty()) {
                EmptyState(message = translatedStringResource(R.string.admin_dashboard_no_active_incidents))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.activeIncidents.forEach { incident ->
                        IncidentListItem(incident = incident, onClick = { onIncidentClick(incident) })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(text = translatedStringResource(R.string.admin_dashboard_high_risk_areas, uiState.topRiskAreaCount), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onViewRiskAreas) { Text(translatedStringResource(R.string.admin_dashboard_view_all)) }
            }
            Text(
                text = translatedStringResource(R.string.admin_dashboard_high_risk_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.topRiskAreas.isEmpty()) {
                EmptyState(message = translatedStringResource(R.string.admin_dashboard_no_high_risk_areas))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.topRiskAreas.forEach { riskScore ->
                        RiskScoreListItem(riskScore = riskScore, onClick = { onViewRiskAreas() })
                    }
                }
            }

            Text(
                text = translatedStringResource(R.string.admin_dashboard_needs_attention),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = translatedStringResource(R.string.admin_dashboard_needs_attention_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.needsAttention.isEmpty()) {
                EmptyState(message = translatedStringResource(R.string.admin_dashboard_nothing_needs_attention))
            } else {
                // FIXED 2026-09-09 (user-reported "dashboard crashes the app") - this was a
                // LazyColumn nested inside the screen's own outer Column, which itself has
                // .verticalScroll(rememberScrollState()) and therefore an unbounded max height.
                // A LazyColumn (or LazyRow) measured against an infinite height constraint throws
                // IllegalStateException at runtime ("Vertically scrollable component was measured
                // with an infinity maximum height constraint") - it needs a bounded height to
                // know how many items to lay out/recycle, which an unbounded scrolling parent
                // never provides. The list is capped at NEEDS_ATTENTION_LIMIT (20) and already
                // sits inside a scrollable parent, so a lazy list bought nothing here anyway - a
                // plain Column (which the outer Column's own scroll happily measures) fixes the
                // crash with no behavior change.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    uiState.needsAttention.forEach { report ->
                        ReportListItem(report = report, isAdmin = true, onClick = { onReportClick(report) })
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
        SummaryStatCard(label = translatedStringResource(R.string.admin_dashboard_total_reports), value = uiState.totalReports.toString(), modifier = Modifier.weight(1f))
        SummaryStatCard(
            label = translatedStringResource(R.string.admin_dashboard_awaiting_action),
            value = ((uiState.countByStatus[ReportStatus.SUBMITTED] ?: 0) + (uiState.countByStatus[ReportStatus.IN_REVIEW] ?: 0)).toString(),
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            label = translatedStringResource(R.string.admin_dashboard_resolved),
            value = (uiState.countByStatus[ReportStatus.RESOLVED] ?: 0).toString(),
            modifier = Modifier.weight(1f),
        )
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
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
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

@Composable
private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> translatedStringResource(R.string.admin_dashboard_status_submitted)
    ReportStatus.IN_REVIEW -> translatedStringResource(R.string.admin_dashboard_status_in_review)
    ReportStatus.RESOLVED -> translatedStringResource(R.string.admin_dashboard_status_resolved)
}
