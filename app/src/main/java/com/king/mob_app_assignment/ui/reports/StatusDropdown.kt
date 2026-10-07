package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> translatedStringResource(R.string.report_detail_status_submitted)
    ReportStatus.IN_REVIEW -> translatedStringResource(R.string.report_detail_status_in_review)
    ReportStatus.RESOLVED -> translatedStringResource(R.string.report_detail_status_resolved)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDropdown(
    currentStatus: ReportStatus,
    onStatusSelected: (ReportStatus) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { expanded = true },
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(currentStatus.label())
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.padding(start = 4.dp),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ReportStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = { Text(status.label()) },
                onClick = {
                    expanded = false
                    if (status != currentStatus) onStatusSelected(status)
                },
            )
        }
    }
}
