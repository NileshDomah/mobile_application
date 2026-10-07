package com.king.mob_app_assignment.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real elevation tiers, used as an actual hierarchy signal rather than every card defaulting to
 * the same flat 0-2dp. A higher tier means "this is the thing your eye should land on first" -
 * the primary CTA and a hero header sit at [floating]; an ordinary content card sits at [raised];
 * a quiet, low-emphasis grouping (a nav-link list, a stat tile) sits at [resting].
 */
object Elevation {
    val flat: Dp = 0.dp
    val resting: Dp = 2.dp
    val raised: Dp = 6.dp
    val floating: Dp = 12.dp
}
