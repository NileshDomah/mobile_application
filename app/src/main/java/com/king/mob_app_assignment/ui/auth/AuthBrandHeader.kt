package com.king.mob_app_assignment.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.theme.Elevation
import com.king.mob_app_assignment.ui.theme.Spacing
import com.king.mob_app_assignment.ui.theme.heroGradient
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Shared branded header shown above the form on both auth screens - the first thing a new user
 * sees, so it's the one deliberate "hero moment" in the app: the logo sits in a raised circular
 * badge over a soft civic-blue-to-teal gradient wash, rather than floating flat on the plain
 * page background the way it used to. Everything else on the auth screens stays quiet by
 * comparison, so this one visual moment doesn't have to compete with anything.
 */
@Composable
fun AuthBrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = Spacing.md)
                .size(104.dp)
                .shadow(elevation = Elevation.floating, shape = CircleShape, ambientColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(heroGradient(darkTheme))
                .padding(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            // The logo's own fills read against a light/near-white ground (its blue shield and
            // amber accents were never designed to sit atop a matching blue gradient) - the
            // gradient forms a glowing ring around a plain surface-colored disc, not a background
            // directly behind the mark itself.
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = translatedStringResource(R.string.auth_logo_content_description),
                modifier = Modifier.size(56.dp),
            )
        }

        Text(
            text = translatedStringResource(R.string.auth_app_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.lg),
        )
    }
}
