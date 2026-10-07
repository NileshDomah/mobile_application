package com.king.mob_app_assignment.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.ui.theme.Elevation
import com.king.mob_app_assignment.ui.theme.heroGradient

/**
 * Full-width, prominent CTA button used for urgent/primary actions across the app
 * (e.g. "Report an Issue", "Submit", "Register"). Filled with the app's own civic-blue gradient
 * (2026-08 restyle - previously a warm amber, which read as an unrelated accent color competing
 * with the rest of the blue-led interface rather than looking like part of the same app) and
 * given a real drop shadow, so this one button reads unmistakably as the single most important
 * action on its screen - everything else is quieter by comparison.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val darkTheme = isSystemInDarkTheme()
    val shape = MaterialTheme.shapes.small
    val isActive = enabled && !isLoading

    Button(
        onClick = onClick,
        enabled = isActive,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = if (isActive) Elevation.floating else Elevation.flat,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            )
            .clip(shape)
            .then(
                if (isActive) {
                    Modifier.background(heroGradient(darkTheme))
                } else {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                },
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            icon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier,
            )
        }
    }
}
