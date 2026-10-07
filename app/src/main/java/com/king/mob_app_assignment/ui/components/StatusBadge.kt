package com.king.mob_app_assignment.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
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
import com.king.mob_app_assignment.ui.theme.translatedStringResource

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

@Composable
private fun labelFor(status: ReportStatus): String = when (status) {
    ReportStatus.SUBMITTED -> translatedStringResource(R.string.status_badge_pending)
    ReportStatus.IN_REVIEW -> translatedStringResource(R.string.status_badge_in_progress)
    ReportStatus.RESOLVED -> translatedStringResource(R.string.status_badge_resolved)
}

/**
 * Small pill badge communicating a [ReportStatus] with the semantic status colors, matching the
 * app-wide "Pending / In Progress / Resolved" convention (also used by the Map screen's pin
 * legend): orange = pending (received, awaiting triage), blue = in progress (being worked),
 * green = resolved. A small solid dot precedes the label - a second, non-color-dependent signal
 * of state (helps anyone with color-vision deficiency, and just reads as more considered than
 * text alone).
 */
@Composable
fun StatusBadge(
    status: ReportStatus,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val style = styleFor(status, darkTheme)
    Row(
        modifier = modifier
            .background(color = style.container, shape = PillShape)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = style.content, shape = CircleShape),
        )
        Text(
            text = labelFor(status),
            style = MaterialTheme.typography.labelMedium,
            color = style.content,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
