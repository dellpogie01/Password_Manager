package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoManager
import com.example.crypto.PasswordStrength
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import com.example.model.UnlockMode

@Composable
fun SetupScreen(viewModel: VaultViewModel, onVaultCreated: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acknowledgmentChecked by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(UnlockMode.FINGERPRINT_ONLY) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var fingerprintCipher by remember { mutableStateOf<Cipher?>(null) }

    val strength = remember(password) { CryptoManager.calculatePasswordStrength(password) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Vault Security",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Password Manager",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Secure offline personal password vault with biometric unlock and AES-256-GCM encryption.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (mode.requiresPassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Password Strength:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = strength.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when (strength) {
                                PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                                PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.secondary
                                PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirm Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = acknowledgmentChecked,
                    onCheckedChange = { acknowledgmentChecked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (mode) {
                        UnlockMode.PASSWORD_ONLY -> "I understand there is no cloud recovery or password reset service. If I forget my master password, I will permanently lose access to my vault."
                        UnlockMode.FINGERPRINT_ONLY -> "I understand there is no cloud recovery. Losing fingerprint access may permanently lock me out of my vault."
                        UnlockMode.PASSWORD_AND_FINGERPRINT -> "I understand that both my master password and fingerprint are required to unlock my vault. If I forget my password or lose fingerprint access, I may permanently lose access to my vault. There is no cloud recovery or password reset service."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            UnlockModeSelector(mode, enabled = !isSubmitting && fingerprintCipher == null) {
                mode = it
                acknowledgmentChecked = false
                errorMessage = null
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (mode.requiresPassword && password.length < 6) {
                        errorMessage = "Password must be at least 6 characters long."
                        return@Button
                    }
                    if (mode.requiresPassword && password != confirmPassword) {
                        errorMessage = "Passwords do not match."
                        return@Button
                    }
                    if (!acknowledgmentChecked) {
                        errorMessage = "Please confirm you understand there is no password recovery."
                        return@Button
                    }
                    if (mode.requiresFingerprint) {
                        try { fingerprintCipher = CryptoManager.getBiometricCipherForEncrypt() }
                        catch (e: Exception) { errorMessage = "Fingerprint unavailable. Enroll a fingerprint or switch off biometric unlock." }
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val success = viewModel.setupVault(password.toCharArray(), mode)
                        isSubmitting = false
                        if (success) {
                            onVaultCreated()
                        } else {
                            errorMessage = "Failed to create vault. Please try again."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    fingerprintCipher?.let { cipher ->
        FingerprintDialog(cipher,
            onAuthenticated = { authenticated ->
                fingerprintCipher = null
                isSubmitting = true
                scope.launch {
                    val success = viewModel.setupVault(if (mode.requiresPassword) password.toCharArray() else charArrayOf(), mode, authenticated)
                    isSubmitting = false
                    if (success) onVaultCreated()
                    else errorMessage = "Failed to create vault. Please try again."
                }
            },
            onError = { fingerprintCipher = null; errorMessage = it },
            onDismiss = { fingerprintCipher = null }
        )
    }

}
