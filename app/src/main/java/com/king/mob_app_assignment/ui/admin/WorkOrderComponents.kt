package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.WorkOrder
import com.king.mob_app_assignment.domain.model.WorkOrderStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.workOrderReferenceCode
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.reports.displayLabel as priorityDisplayLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Work order list + create action, embedded on IncidentDetailScreen. A separate composable file
 * (not inlined into IncidentDetailScreen.kt) since work orders are their own reasonably-sized
 * concern (creation dialog, status display, list) - matches how ReportListComponents.kt is its
 * own file rather than living inside ReportsListScreen.kt.
 */
@Composable
fun WorkOrdersSection(
    workOrders: List<WorkOrder>,
    onCreateWorkOrder: (Department?, String, ReportPriority, Long?) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = translatedStringResource(R.string.work_orders_title, workOrders.size), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showCreateDialog = true }) { Text(translatedStringResource(R.string.work_orders_new)) }
        }
        if (workOrders.isEmpty()) {
            Text(
                text = translatedStringResource(R.string.work_orders_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            workOrders.forEach { workOrder ->
                WorkOrderCard(workOrder = workOrder, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreateWorkOrderDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { department, team, priority ->
                onCreateWorkOrder(department, team, priority, null)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun WorkOrderCard(workOrder: WorkOrder, modifier: Modifier = Modifier) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = workOrderReferenceCode(workOrder.id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = workOrder.status.displayLabel(), style = MaterialTheme.typography.labelMedium)
            }
            val unassignedLabel = translatedStringResource(R.string.work_orders_unassigned)
            Text(
                text = workOrder.department?.displayLabel()?.let {
                    translatedStringResource(R.string.work_orders_department_team, it, workOrder.assignedTeam.ifBlank { unassignedLabel })
                } ?: workOrder.assignedTeam.ifBlank { unassignedLabel },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = translatedStringResource(R.string.work_orders_created, dateFormat.format(Date(workOrder.createdAt))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (workOrder.repairNotes.isNotBlank()) {
                Text(text = workOrder.repairNotes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CreateWorkOrderDialog(
    onDismiss: () -> Unit,
    onCreate: (Department?, String, ReportPriority) -> Unit,
) {
    var department by remember { mutableStateOf<Department?>(null) }
    var team by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(ReportPriority.NORMAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(translatedStringResource(R.string.work_orders_dialog_title)) },
        text = {
            Column {
                Text(text = translatedStringResource(R.string.work_orders_department_label), style = MaterialTheme.typography.labelLarge)
                LazyRow(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                    items(Department.entries.toList()) { dept ->
                        CategoryChip(
                            label = dept.displayLabel(),
                            selected = department == dept,
                            onClick = { department = if (department == dept) null else dept },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = team,
                    onValueChange = { team = it },
                    label = { Text(translatedStringResource(R.string.work_orders_assigned_team_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = translatedStringResource(R.string.work_orders_priority_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    ReportPriority.entries.forEach { p ->
                        CategoryChip(
                            label = p.priorityDisplayLabel(),
                            selected = priority == p,
                            onClick = { priority = p },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(department, team.trim(), priority) }, enabled = team.isNotBlank()) {
                Text(translatedStringResource(R.string.work_orders_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(translatedStringResource(R.string.work_orders_cancel)) }
        },
    )
}
