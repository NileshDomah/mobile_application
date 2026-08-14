package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.StatusHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vertical timeline of every status transition a report has gone through, oldest first. Gives
 * citizens (and admins) a professional, trackable audit trail rather than only ever showing the
 * current status - similar to a courier/delivery tracking view. Falls back gracefully to a
 * single "Submitted" entry for reports created before this feature existed (statusHistory empty).
 */
@Composable
fun StatusTimelineCard(
    history: List<StatusHistoryEntry>,
    fallbackStatus: ReportStatus,
    fallbackTimestamp: Long,
    modifier: Modifier = Modifier,
) {
    val entries = history.ifEmpty {
        listOf(StatusHistoryEntry(status = fallbackStatus, timestamp = fallbackTimestamp))
    }
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Status history", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(top = 12.dp)) {
                entries.forEachIndexed { index, entry ->
                    TimelineRow(
                        entry = entry,
                        isCurrent = index == entries.lastIndex,
                        isLast = index == entries.lastIndex,
                        timestampText = dateFormat.format(Date(entry.timestamp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: StatusHistoryEntry,
    isCurrent: Boolean,
    isLast: Boolean,
    timestampText: String,
) {
    val dotColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.width(24.dp).padding(top = 4.dp)) {
                val radius = 5.dp.toPx()
                drawCircle(color = dotColor, radius = radius, center = Offset(size.width / 2f, radius))
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width / 2f, radius * 2),
                        end = Offset(size.width / 2f, size.height.coerceAtLeast(56.dp.toPx())),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 8.dp, bottom = if (isLast) 0.dp else 20.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = entry.status.timelineLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = timestampText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun ReportStatus.timelineLabel(): String = when (this) {
    ReportStatus.SUBMITTED -> "Report submitted"
    ReportStatus.IN_REVIEW -> "In review by the city"
    ReportStatus.RESOLVED -> "Resolved"
}
