package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.crypto.CryptoManager
import com.example.model.UnlockMode
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import javax.crypto.Cipher

@Composable
fun UnlockModeSettingsDialog(viewModel: VaultViewModel, current: UnlockMode, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(current) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cipher by remember { mutableStateOf<Cipher?>(null) }
    val scope = rememberCoroutineScope()
    fun save(authenticated: Cipher? = null) {
        scope.launch {
            val success = viewModel.setUnlockMode(mode,
                if (mode.requiresPassword) password.toCharArray() else charArrayOf(), authenticated)
            busy = false
            if (success) onDismiss() else error = "Unable to change unlock method. Your previous method is still active."
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Unlock method") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UnlockModeSelector(mode, !busy) { mode = it; error = null }
                if (mode.requiresPassword) {
                    Text("Set the master password for this unlock method.")
                    OutlinedTextField(value = password, onValueChange = { password = it; error = null },
                        label = { Text("Master Password") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(value = confirm, onValueChange = { confirm = it; error = null },
                        label = { Text("Confirm Master Password") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation())
                }
                if (mode == UnlockMode.PASSWORD_AND_FINGERPRINT) Text("Both password and fingerprint will be required at every unlock.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (mode.requiresPassword && password.length < 6) {
                    error = "Password must be at least 6 characters."
                } else if (mode.requiresPassword && password != confirm) {
                    error = "Passwords do not match."
                } else {
                    busy = true
                    if (mode.requiresFingerprint) {
                        try { cipher = CryptoManager.getBiometricCipherForEncrypt() }
                        catch (e: Exception) { busy = false; error = "Enroll a fingerprint in device settings first." }
                    } else save()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
    cipher?.let { currentCipher ->
        FingerprintDialog(currentCipher,
            onAuthenticated = { cipher = null; save(it) },
            onError = { cipher = null; busy = false; error = it },
            onDismiss = { cipher = null; busy = false })
    }
}
