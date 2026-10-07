package com.king.mob_app_assignment.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spacing throughout the app. Previously every screen picked its own
 * 8/12/16/24dp values by convention rather than a shared scale, which is how small
 * inconsistencies crept in screen to screen. Everything here is a multiple of 4dp.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}
