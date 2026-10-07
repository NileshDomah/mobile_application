package com.king.mob_app_assignment.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

const val PIN_LENGTH = 6

/**
 * Reused by both SetupAppLockScreen (creating a PIN) and AppLockScreen (entering it to unlock) -
 * a row of filled/empty dot indicators showing progress toward PIN_LENGTH digits, plus a standard
 * 0-9 + backspace numeric keypad. Purely a dumb input widget: callers own the actual digit string
 * and decide when a full PIN should be validated/submitted (see onDigit/onBackspace).
 */
@Composable
fun PinPad(
    enteredLength: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 32.dp),
        ) {
            repeat(PIN_LENGTH) { index ->
                val filled = index < enteredLength
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }

        val rows = listOf("123", "456", "789", " 0⌫")
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                row.forEach { char ->
                    when (char) {
                        ' ' -> Box(modifier = Modifier.size(72.dp)) // empty spacer key
                        '⌫' -> PinPadKey(onClick = onBackspace) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                        }
                        else -> PinPadKey(onClick = { onDigit(char) }) {
                            Text(text = char.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPadKey(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
