package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Incident
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.incidentReferenceCode
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.dotColor
import com.king.mob_app_assignment.ui.reports.displayLabel as categoryDisplayLabel
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
fun AdminIncidentsScreen(
    onIncidentClick: (Incident) -> Unit,
    viewModel: AdminIncidentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val dismissLabel = translatedStringResource(R.string.admin_incidents_dismiss)
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = dismissLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.dismissError() else viewModel.dismissError()
    }

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.admin_incidents_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            FilterChip(
                selected = uiState.showClosedAndDismissed,
                onClick = { viewModel.onShowClosedToggle(!uiState.showClosedAndDismissed) },
                label = { Text(translatedStringResource(R.string.admin_incidents_show_closed_dismissed)) },
            )

            if (uiState.isLoading) {
                LoadingIndicator()
            } else if (uiState.incidents.isEmpty()) {
                EmptyState(
                    message = translatedStringResource(R.string.admin_incidents_empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.incidents, key = { it.id }) { incident ->
                        IncidentListItem(incident = incident, onClick = { onIncidentClick(incident) })
                    }
                }
            }
        }
    }
}

@Composable
fun IncidentListItem(incident: Incident, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = incident.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IncidentStatusBadge(incident)
            }
            Text(
                text = incidentReferenceCode(incident.id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = translatedStringResource(R.string.admin_incidents_report_count, incident.reportCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = incident.category.categoryDisplayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// A pill container (matching StatusBadge's shape/spacing) rather than the bare dot+text this used
// to be, for visual consistency with report status badges elsewhere - still keyed on
// incident.severity for its color (severity conveys urgency) while the label itself is the
// incident's own status (a distinct concept from a single report's status), so this intentionally
// stays a separate composable from StatusBadge rather than being merged into it.
@Composable
fun IncidentStatusBadge(incident: Incident) {
    val dotColor = incident.severity.dotColor(MaterialTheme.colorScheme)
    Row(
        modifier = Modifier
            .background(color = dotColor.copy(alpha = 0.14f), shape = com.king.mob_app_assignment.ui.theme.PillShape)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = dotColor, shape = CircleShape),
        )
        Text(
            text = incident.status.displayLabel(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = dotColor,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
