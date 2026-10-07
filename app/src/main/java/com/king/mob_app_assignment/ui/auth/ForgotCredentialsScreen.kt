package com.king.mob_app_assignment.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * Two independent recovery actions from one email field: look up the account's username (only
 * works for accounts registered after the /emails reservation doc was introduced - see
 * AuthRepositoryImpl.register()), and/or send Firebase's own built-in password-reset email.
 * Deliberately does not claim "if this email doesn't exist you'll be told" - Firebase's reset
 * email call does not reliably distinguish that case, and the username lookup silently returning
 * nothing looks identical whether the email has no account or simply predates username tracking,
 * by design (this screen never confirms/denies whether a given email has an account at all,
 * standard account-enumeration-prevention practice).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotCredentialsScreen(
    onBackClick: () -> Unit,
    viewModel: ForgotCredentialsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.forgot_credentials_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = translatedStringResource(R.string.forgot_credentials_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(translatedStringResource(R.string.forgot_credentials_email_label)) },
                singleLine = true,
                isError = uiState.emailError != null,
                supportingText = { uiState.emailError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            PrimaryActionButton(
                text = translatedStringResource(R.string.forgot_credentials_find_username),
                onClick = viewModel::findUsername,
                isLoading = uiState.isLookingUpUsername,
            )

            if (uiState.usernameLookupAttempted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        if (uiState.foundUsername != null) {
                            Text(text = translatedStringResource(R.string.forgot_credentials_username_found_label), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = uiState.foundUsername!!,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        } else {
                            Text(
                                text = translatedStringResource(R.string.forgot_credentials_username_not_found),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::sendPasswordResetEmail,
                enabled = !uiState.isSendingResetEmail,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    if (uiState.isSendingResetEmail) {
                        translatedStringResource(R.string.forgot_credentials_sending)
                    } else {
                        translatedStringResource(R.string.forgot_credentials_send_reset_email)
                    },
                )
            }

            if (uiState.resetEmailSent) {
                Text(
                    text = translatedStringResource(R.string.forgot_credentials_reset_email_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
