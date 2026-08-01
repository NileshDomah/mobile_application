package com.king.mob_app_assignment.ui.reports

import androidx.compose.runtime.Composable
import com.king.mob_app_assignment.domain.model.Report

@Composable
fun MyReportsScreen(onReportClick: (Report) -> Unit) {
    ReportsListScreen(title = "My Reports", scope = ReportsScope.MINE, onReportClick = onReportClick)
}
