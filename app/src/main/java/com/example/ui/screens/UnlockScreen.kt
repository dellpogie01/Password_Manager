package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.crypto.CryptoManager
import com.example.model.UnlockMode
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import javax.crypto.Cipher

@Composable
fun UnlockScreen(viewModel: VaultViewModel, onUnlocked: () -> Unit) {
    val mode by viewModel.unlockMode.collectAsState()
    var password by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cipher by remember { mutableStateOf<Cipher?>(null) }
    val scope = rememberCoroutineScope()

    fun beginUnlock() {
        if (mode.requiresPassword && password.isEmpty()) {
            error = "Enter your master password."
            return
        }
        busy = true
        error = null
        scope.launch {
            if (mode == UnlockMode.PASSWORD_ONLY) {
                val success = viewModel.unlockWithMasterPassword(password.toCharArray())
                busy = false
                if (success) onUnlocked() else error = "Incorrect master password."
            } else {
                try {
                    val iv = viewModel.getMeta()?.biometricKeyIv
                        ?: kotlin.error("Fingerprint is not configured. Enable it again in Settings.")
                    pendingPassword?.fill('\u0000')
                    pendingPassword = password.toCharArray()
                    cipher = CryptoManager.getBiometricCipherForDecrypt(iv)
                } catch (e: Exception) {
                    busy = false
                    error = "Fingerprint unavailable. ${e.message ?: "Please try again."}"
                }
            }
        }
    }

    LaunchedEffect(mode) {
        if (mode == UnlockMode.FINGERPRINT_ONLY) beginUnlock()
    }
    DisposableEffect(Unit) {
        onDispose { pendingPassword?.fill('\u0000') }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Password Manager Locked", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(when (mode) {
                UnlockMode.PASSWORD_ONLY -> "Enter your master password to unlock."
                UnlockMode.FINGERPRINT_ONLY -> "Use your fingerprint to unlock."
                UnlockMode.PASSWORD_AND_FINGERPRINT -> "Enter your master password, then verify your fingerprint. Both are required."
            })
            Spacer(Modifier.height(24.dp))
            if (mode.requiresPassword) {
                OutlinedTextField(value = password, onValueChange = { password = it; error = null },
                    label = { Text("Master Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), enabled = !busy,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { beginUnlock() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Unlocking..." else if (mode.requiresFingerprint) "Continue with Fingerprint" else "Unlock Vault")
            }
        }
    }
    cipher?.let { current ->
        FingerprintDialog(current,
            onAuthenticated = { authenticated ->
                cipher = null
                val suppliedPassword = pendingPassword ?: charArrayOf()
                pendingPassword = null
                scope.launch {
                    try {
                        if (viewModel.unlockWithBiometric(authenticated, suppliedPassword)) onUnlocked()
                        else error = "Unable to unlock. Check your password and try fingerprint authentication again."
                    } finally { suppliedPassword.fill('\u0000'); busy = false }
                }
            },
            onError = { cipher = null; pendingPassword?.fill('\u0000'); pendingPassword = null; busy = false; error = it },
            onDismiss = { cipher = null; pendingPassword?.fill('\u0000'); pendingPassword = null; busy = false }
        )
    }
}
