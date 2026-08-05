package com.king.mob_app_assignment.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.domain.model.ImpactStats
import com.king.mob_app_assignment.ui.theme.StatusResolvedDark
import com.king.mob_app_assignment.ui.theme.StatusResolvedDarkContainer
import com.king.mob_app_assignment.ui.theme.StatusResolvedLight
import com.king.mob_app_assignment.ui.theme.StatusResolvedLightContainer

/**
 * Compact two-tile stat row summarizing a citizen's own reporting impact: how many reports
 * they've filed in total, and how many of those have been marked resolved.
 */
@Composable
fun StatsRow(
    stats: ImpactStats,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val resolvedContent = if (darkTheme) StatusResolvedDark else StatusResolvedLight
    val resolvedContainer = if (darkTheme) StatusResolvedDarkContainer else StatusResolvedLightContainer

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            icon = Icons.Filled.Assignment,
            value = stats.totalReports,
            label = "Reports Filed",
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Filled.CheckCircle,
            value = stats.resolvedReports,
            label = "Resolved",
            contentColor = resolvedContent,
            containerColor = resolvedContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.StatTile(
    icon: ImageVector,
    value: Int,
    label: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color = containerColor, shape = MaterialTheme.shapes.medium)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
