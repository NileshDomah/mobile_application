package com.king.mob_app_assignment.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.king.mob_app_assignment.ui.theme.Elevation
import com.king.mob_app_assignment.ui.theme.Spacing
import com.king.mob_app_assignment.ui.theme.StatusResolvedDark
import com.king.mob_app_assignment.ui.theme.StatusResolvedDarkContainer
import com.king.mob_app_assignment.ui.theme.StatusResolvedLight
import com.king.mob_app_assignment.ui.theme.StatusResolvedLightContainer

/**
 * Compact two-tile stat row summarizing a citizen's own reporting impact: how many reports
 * they've filed in total, and how many of those have been marked resolved. Each tile is a real
 * elevated card with a hairline border in its own accent color, so the row reads as two distinct,
 * tactile objects rather than two flat color fills sitting on the page.
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatTile(
            icon = Icons.AutoMirrored.Filled.Assignment,
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
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.resting),
        border = BorderStroke(width = 1.dp, color = contentColor.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = contentColor.copy(alpha = 0.14f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}
