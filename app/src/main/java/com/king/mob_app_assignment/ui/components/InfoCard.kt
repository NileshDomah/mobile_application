package com.king.mob_app_assignment.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.king.mob_app_assignment.ui.theme.Elevation
import com.king.mob_app_assignment.ui.theme.Spacing

/**
 * A message-type severity, mapping to the app's existing color roles rather than inventing new
 * ones - [Warning]/[Positive] reuse error/tertiary so no new hues enter the palette.
 */
enum class InfoCardSeverity { Info, Warning, Positive }

/**
 * A single, shared "message card" for the alert/notice moments scattered across the app - the
 * offline-sync banner on Home, the AI-review-needed notice and the "update from the city" card
 * on ReportDetail all used to be separately hand-built Cards that looked almost identical in
 * structure but only differed by container color. This makes the severity visually legible at a
 * glance (an accent stripe down the leading edge, plus a tinted icon) instead of relying on
 * container-color alone to communicate "this is a warning" vs "this is good news."
 */
@Composable
fun InfoCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    severity: InfoCardSeverity = InfoCardSeverity.Info,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val accent: Color = when (severity) {
        InfoCardSeverity.Info -> MaterialTheme.colorScheme.primary
        InfoCardSeverity.Warning -> MaterialTheme.colorScheme.error
        InfoCardSeverity.Positive -> MaterialTheme.colorScheme.tertiary
    }
    val container: Color = when (severity) {
        InfoCardSeverity.Info -> MaterialTheme.colorScheme.primaryContainer
        InfoCardSeverity.Warning -> MaterialTheme.colorScheme.errorContainer
        InfoCardSeverity.Positive -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer: Color = when (severity) {
        InfoCardSeverity.Info -> MaterialTheme.colorScheme.onPrimaryContainer
        InfoCardSeverity.Warning -> MaterialTheme.colorScheme.onErrorContainer
        InfoCardSeverity.Positive -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.resting),
        border = BorderStroke(width = 1.dp, color = accent.copy(alpha = 0.25f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            // Leading accent stripe - a thin solid bar in the severity color, the strongest
            // at-a-glance cue that this card carries a specific kind of message.
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(color = accent, shape = MaterialTheme.shapes.extraSmall),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(start = Spacing.sm, end = Spacing.sm)
                    .align(Alignment.CenterVertically),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            trailingContent?.invoke()
        }
    }
}
