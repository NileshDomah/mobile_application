package com.king.mob_app_assignment.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Placeholder card mirroring ReportListItem's layout (icon box + a few text-line bars),
 * pulsing between two surface-variant tones to signal "content is loading" while the
 * Firestore snapshot listener delivers its first batch.
 */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonShimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SkeletonBox(
                color = shimmerColor,
                modifier = Modifier.size(40.dp).padding(end = 12.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(color = shimmerColor, modifier = Modifier.fillMaxWidth(0.5f).height(16.dp))
                SkeletonBox(color = shimmerColor, modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
                SkeletonBox(color = shimmerColor, modifier = Modifier.fillMaxWidth(0.35f).height(12.dp))
                Spacer(modifier = Modifier.height(2.dp))
                SkeletonBox(color = shimmerColor, modifier = Modifier.width(70.dp).height(20.dp))
            }
        }
    }
}

@Composable
private fun SkeletonBox(color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color)) {}
}

/**
 * Stack of skeleton placeholders standing in for a loading list, used across the reports
 * list, admin list, and Home's recent-activity section instead of a full-screen spinner.
 */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, count: Int = 4) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            SkeletonListItem()
        }
    }
}
