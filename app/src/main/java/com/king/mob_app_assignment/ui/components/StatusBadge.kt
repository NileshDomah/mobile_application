package com.king.mob_app_assignment.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.ui.theme.PillShape
import com.king.mob_app_assignment.ui.theme.StatusInReviewDark
import com.king.mob_app_assignment.ui.theme.StatusInReviewDarkContainer
import com.king.mob_app_assignment.ui.theme.StatusInReviewLight
import com.king.mob_app_assignment.ui.theme.StatusInReviewLightContainer
import com.king.mob_app_assignment.ui.theme.StatusResolvedDark
import com.king.mob_app_assignment.ui.theme.StatusResolvedDarkContainer
import com.king.mob_app_assignment.ui.theme.StatusResolvedLight
import com.king.mob_app_assignment.ui.theme.StatusResolvedLightContainer
import com.king.mob_app_assignment.ui.theme.StatusSubmittedDark
import com.king.mob_app_assignment.ui.theme.StatusSubmittedDarkContainer
import com.king.mob_app_assignment.ui.theme.StatusSubmittedLight
import com.king.mob_app_assignment.ui.theme.StatusSubmittedLightContainer

private data class StatusStyle(val container: Color, val content: Color)

private fun styleFor(status: ReportStatus, darkTheme: Boolean): StatusStyle = when (status) {
    ReportStatus.SUBMITTED -> if (darkTheme) {
        StatusStyle(StatusSubmittedDarkContainer, StatusSubmittedDark)
    } else {
        StatusStyle(StatusSubmittedLightContainer, StatusSubmittedLight)
    }
    ReportStatus.IN_REVIEW -> if (darkTheme) {
        StatusStyle(StatusInReviewDarkContainer, StatusInReviewDark)
    } else {
        StatusStyle(StatusInReviewLightContainer, StatusInReviewLight)
    }
    ReportStatus.RESOLVED -> if (darkTheme) {
        StatusStyle(StatusResolvedDarkContainer, StatusResolvedDark)
    } else {
        StatusStyle(StatusResolvedLightContainer, StatusResolvedLight)
    }
}

private fun labelFor(status: ReportStatus): String = when (status) {
    ReportStatus.SUBMITTED -> "Submitted"
    ReportStatus.IN_REVIEW -> "In review"
    ReportStatus.RESOLVED -> "Resolved"
}

/**
 * Small pill badge communicating a [ReportStatus] with the semantic status colors:
 * blue = submitted (received), amber = in review (being worked), green = resolved.
 */
@Composable
fun StatusBadge(
    status: ReportStatus,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val style = styleFor(status, darkTheme)
    Text(
        text = labelFor(status),
        style = MaterialTheme.typography.labelMedium,
        color = style.content,
        modifier = modifier
            .background(color = style.container, shape = PillShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
