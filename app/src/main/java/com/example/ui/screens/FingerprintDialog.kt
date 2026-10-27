package com.example.ui.screens

import android.hardware.fingerprint.FingerprintManager
import android.os.CancellationSignal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import javax.crypto.Cipher

// FingerprintManager intentionally restricts authentication to fingerprints.
@Suppress("DEPRECATION")
@Composable
fun FingerprintDialog(
    cipher: Cipher,
    onAuthenticated: (Cipher) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val success by rememberUpdatedState(onAuthenticated)
    val failure by rememberUpdatedState(onError)
    var message by remember { mutableStateOf("Touch your device's fingerprint sensor.") }
    DisposableEffect(cipher) {
        val cancellation = CancellationSignal()
        var active = true
        try {
            val manager = context.getSystemService(FingerprintManager::class.java)
            require(manager != null && manager.isHardwareDetected && manager.hasEnrolledFingerprints()) {
                "Enroll a fingerprint in your device settings, or switch off fingerprint unlock to use a master password."
            }
            manager.authenticate(FingerprintManager.CryptoObject(cipher), cancellation, 0,
                object : FingerprintManager.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
                        if (!active) return
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher != null) success(authenticatedCipher)
                        else failure("Fingerprint authentication did not authorize vault access.")
                    }
                    override fun onAuthenticationFailed() {
                        if (active) message = "Fingerprint not recognized. Try again."
                    }
                    override fun onAuthenticationHelp(code: Int, help: CharSequence) {
                        if (active) message = help.toString()
                    }
                    override fun onAuthenticationError(code: Int, error: CharSequence) {
                        if (active) failure(error.toString())
                    }
                }, null)
        } catch (e: Exception) {
            failure(e.message ?: "Fingerprint unlock is unavailable.")
        }
        onDispose { active = false; cancellation.cancel() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fingerprint Unlock") },
        text = { Text(message) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
