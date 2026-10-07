package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.IncidentStatus
import com.king.mob_app_assignment.domain.model.IncidentValidationState
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.WorkOrderStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.workOrderReferenceCode
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.ReportListItem
import com.king.mob_app_assignment.ui.reports.displayLabel as priorityDisplayLabel
import com.king.mob_app_assignment.ui.reports.dotColor
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
fun IncidentDetailScreen(
    onBackClick: () -> Unit,
    onReportClick: (String) -> Unit,
    viewModel: IncidentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var validationNote by remember { mutableStateOf("") }
    var showMergeCandidates by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.incident_detail_title), onBackClick = onBackClick) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { LoadingIndicator() }
            return@Scaffold
        }
        val incident = uiState.incident
        if (incident == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(translatedStringResource(R.string.incident_detail_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(text = incident.title, style = MaterialTheme.typography.headlineSmall)
                IncidentStatusBadge(incident)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = translatedStringResource(R.string.incident_detail_member_reports_count, incident.reportCount))
                    Text(text = incident.address.ifBlank { translatedStringResource(R.string.incident_detail_location_unavailable) }, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Transparent priority score - every factor shown, not just the total, per the
            // brief's explicit "show why the priority was calculated" requirement.
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = translatedStringResource(R.string.incident_detail_priority_score_title), style = MaterialTheme.typography.titleMedium)
                        Text(text = translatedStringResource(R.string.incident_detail_priority_score_value, incident.priorityScore), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    if (incident.priorityFactors.isEmpty()) {
                        Text(
                            text = translatedStringResource(R.string.incident_detail_priority_not_calculated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        incident.priorityFactors.forEach { factor ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = factor.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = factor.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(text = translatedStringResource(R.string.incident_detail_priority_points, factor.points), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Validation - explicit admin agree/reject, distinct from the status pipeline below.
            Column {
                Text(text = translatedStringResource(R.string.incident_detail_validation_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (incident.validationState) {
                        IncidentValidationState.PENDING -> translatedStringResource(R.string.incident_detail_validation_pending)
                        IncidentValidationState.VALIDATED -> translatedStringResource(R.string.incident_detail_validation_validated)
                        IncidentValidationState.REJECTED -> translatedStringResource(R.string.incident_detail_validation_rejected)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.validate(validationNote) }) { Text(translatedStringResource(R.string.incident_detail_validate)) }
                    TextButton(onClick = { viewModel.reject(validationNote) }) { Text(translatedStringResource(R.string.incident_detail_reject)) }
                }
            }

            Text(text = translatedStringResource(R.string.incident_detail_status_title), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IncidentStatus.entries) { status ->
                    CategoryChip(
                        label = status.displayLabel(),
                        selected = incident.status == status,
                        onClick = { viewModel.updateStatus(status) },
                    )
                }
            }

            Text(text = translatedStringResource(R.string.incident_detail_severity_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = incident.severity == priority,
                        onClick = { viewModel.setSeverity(priority) },
                        label = { Text(priority.priorityDisplayLabel()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = priority.dotColor(MaterialTheme.colorScheme).copy(alpha = 0.18f),
                            selectedLabelColor = priority.dotColor(MaterialTheme.colorScheme),
                        ),
                    )
                }
            }

            Text(text = translatedStringResource(R.string.incident_detail_department_title), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Department.entries.toList()) { department ->
                    CategoryChip(
                        label = department.displayLabel(),
                        selected = incident.department == department,
                        onClick = { viewModel.assignDepartment(if (incident.department == department) null else department) },
                    )
                }
            }

            WorkOrdersSection(
                workOrders = uiState.workOrders,
                onCreateWorkOrder = { department, team, priority, dueDate ->
                    viewModel.createWorkOrder(department, team, priority, dueDate)
                },
            )

            Column {
                Text(text = translatedStringResource(R.string.incident_detail_validation_note_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = translatedStringResource(R.string.incident_detail_validation_note_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = validationNote.ifBlank { incident.validationNote },
                    onValueChange = { validationNote = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2,
                )
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.setValidationNote(validationNote) },
                    enabled = validationNote.isNotBlank(),
                ) { Text(translatedStringResource(R.string.incident_detail_save_note)) }
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = translatedStringResource(R.string.incident_detail_member_reports_title, uiState.memberReports.size), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showMergeCandidates = !showMergeCandidates }) {
                        Icon(Icons.Filled.Add, contentDescription = translatedStringResource(R.string.incident_detail_merge_another))
                    }
                }
                uiState.memberReports.forEach { report ->
                    ReportListItem(
                        report = report,
                        isAdmin = true,
                        onClick = { onReportClick(report.id) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.unmergeReport(report.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = translatedStringResource(R.string.incident_detail_unmerge))
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (showMergeCandidates) {
                Column {
                    Text(text = translatedStringResource(R.string.incident_detail_nearby_reports_title), style = MaterialTheme.typography.titleMedium)
                    if (uiState.mergeCandidates.isEmpty()) {
                        Text(
                            text = translatedStringResource(R.string.incident_detail_no_nearby_reports),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.mergeCandidates.forEach { report ->
                            ReportListItem(
                                report = report,
                                isAdmin = true,
                                onClick = { onReportClick(report.id) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.mergeReport(report.id) }) {
                                        Icon(Icons.Filled.Add, contentDescription = translatedStringResource(R.string.incident_detail_merge_into))
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            AuditHistorySection(entries = uiState.auditHistory)
        }
    }
}
