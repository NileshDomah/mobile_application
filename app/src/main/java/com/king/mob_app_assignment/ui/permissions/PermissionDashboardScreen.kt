package com.king.mob_app_assignment.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.theme.PillShape
import com.king.mob_app_assignment.ui.theme.StatusResolvedLight
import com.king.mob_app_assignment.ui.theme.StatusResolvedLightContainer
import com.king.mob_app_assignment.util.PermissionManager

@Composable
fun PermissionDashboardScreen(
    viewModel: PermissionDashboardViewModel,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { AppTopBar(title = "Permission Dashboard", onBackClick = onBackClick) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.statuses) { status ->
                PermissionRow(
                    status = status,
                    onGrantClick = {
                        permissionLauncher.launch(status.permission.manifestPermission)
                    },
                    onOpenSettingsClick = {
                        context.startActivity(PermissionManager.appSettingsIntent(context))
                    },
                )
            }
        }
    }
}

@Composable
private fun GrantedStatusChip(isGranted: Boolean, modifier: Modifier = Modifier) {
    val container = if (isGranted) {
        StatusResolvedLightContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (isGranted) StatusResolvedLight else MaterialTheme.colorScheme.onErrorContainer
    Text(
        text = if (isGranted) "Granted" else "Not granted",
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .background(color = container, shape = PillShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun PermissionRow(
    status: PermissionStatusUi,
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status.isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (status.isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(status.permission.label, style = MaterialTheme.typography.titleMedium)
                    GrantedStatusChip(isGranted = status.isGranted, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (!status.isGranted) {
                val permanentlyDenied = activity != null &&
                    PermissionManager.isPermanentlyDenied(activity, status.permission)
                Button(
                    onClick = if (permanentlyDenied) onOpenSettingsClick else onGrantClick,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (permanentlyDenied) "Open Settings" else "Grant")
                }
            }
        }
    }
}
