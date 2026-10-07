package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.AuditLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Read-only audit trail for an incident - every status/department/severity/validation/work-order
 * change recorded via AuditLogRepository.record() surfaces here with who/when/previous/new/note,
 * per the brief's "no important administrative action without traceability" requirement. Kept as
 * its own small file (mirrors WorkOrderComponents.kt) since IncidentDetailScreen.kt is already
 * long and this is a self-contained, purely-presentational concern.
 */
@Composable
fun AuditHistorySection(entries: List<AuditLogEntry>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column {
        Text(text = translatedStringResource(R.string.audit_history_title, entries.size), style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) {
            Text(
                text = translatedStringResource(R.string.audit_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.sortedByDescending { it.timestamp }.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.flat),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = entry.action, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (entry.previousValue.isNotBlank() || entry.newValue.isNotBlank()) {
                            val placeholder = translatedStringResource(R.string.audit_history_value_placeholder)
                            Text(
                                text = stringResource(
                                    R.string.audit_history_value_change,
                                    entry.previousValue.ifBlank { placeholder },
                                    entry.newValue.ifBlank { placeholder },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.note.isNotBlank()) {
                            Text(text = entry.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = dateFormat.format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
