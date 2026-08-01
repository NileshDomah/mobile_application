package com.king.mob_app_assignment.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Single consistent shape language for the whole app: generous rounded corners,
 * scaling up with component size. This is the softer "modern civic-tech" reading
 * (approachable, not bureaucratic) while remaining clean and geometric.
 *
 * extraSmall 8dp  - chips, badges, small controls
 * small      12dp - buttons, text fields
 * medium     16dp - cards, dialogs
 * large      24dp - bottom sheets, prominent CTA containers
 * extraLarge 28dp - full-screen containers / large sheets
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Dedicated radius for the pill-shaped StatusBadge / chips, kept fully rounded regardless of size. */
val PillShape = RoundedCornerShape(percent = 50)
