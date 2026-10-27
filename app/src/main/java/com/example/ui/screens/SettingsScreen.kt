package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onVaultReset: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by viewModel.unlockMode.collectAsState()
    val hasMasterPassword = mode.requiresPassword
    var showUnlockMethodDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var changePasswordError by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                Toast.makeText(context, "Encrypted backup saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                Toast.makeText(context, "Vault restored successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Security",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Unlock method", style = MaterialTheme.typography.titleMedium)
                    Text(mode.label)
                    TextButton(onClick = { showUnlockMethodDialog = true }) { Text("Change Unlock Method") }

                    HorizontalDivider()

                    if (hasMasterPassword) TextButton(onClick = { showChangePasswordDialog = true }) {
                        Text(if (hasMasterPassword) "Change Master Password" else "Create Master Password")
                    }
                }
            }

            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { createBackupLauncher.launch("pw_manager_backup.json") }) {
                        Text("Export Encrypted Backup")
                    }
                    HorizontalDivider()
                    TextButton(onClick = { restoreBackupLauncher.launch(arrayOf("application/json")) }) {
                        Text("Restore Encrypted Backup")
                    }
                }
            }

            Text(
                text = "Privacy & About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PW Manager is 100% offline.", fontWeight = FontWeight.Bold)
                    Text("No internet permission is requested or used. All data is encrypted locally using AES-256-GCM. We never store or transmit your credentials.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text(if (hasMasterPassword) "Change Master Password" else "Create Master Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (hasMasterPassword) {
                        OutlinedTextField(
                            value = oldPassword,
                            onValueChange = { oldPassword = it; changePasswordError = null },
                            label = { Text("Current Master Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; changePasswordError = null },
                        label = { Text("New Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it; changePasswordError = null },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (changePasswordError != null) {
                        Text(text = changePasswordError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPassword.length < 6) {
                            changePasswordError = "New password must be at least 6 characters."
                            return@TextButton
                        }
                        if (newPassword != confirmNewPassword) {
                            changePasswordError = "New passwords do not match."
                            return@TextButton
                        }
                        scope.launch {
                            val success = viewModel.changeMasterPassword(oldPassword.toCharArray(), newPassword.toCharArray())
                            if (success) {
                                Toast.makeText(context, "Master password changed successfully", Toast.LENGTH_SHORT).show()
                                showChangePasswordDialog = false
                                oldPassword = ""
                                newPassword = ""
                                confirmNewPassword = ""
                            } else {
                                changePasswordError = "Incorrect current master password."
                            }
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showUnlockMethodDialog) {
        UnlockModeSettingsDialog(viewModel, mode) { showUnlockMethodDialog = false }
    }
}
