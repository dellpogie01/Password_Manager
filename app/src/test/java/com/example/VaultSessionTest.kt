package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.model.VaultEntry
import com.example.model.UnlockMode
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultSessionTest {
    @org.junit.Before
    fun clearVault() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dao = com.example.data.VaultDatabase.getDatabase(application).vaultDao()
        dao.deleteAllEntries()
        dao.deleteMeta()
    }

    @Test
    fun `setup unlock and lock share one session and clear decrypted entries`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VaultViewModel(application)
        try {
            runBlocking {
                withTimeout(10_000) { viewModel.isLoading.first { !it } }
                assertTrue(viewModel.setupVault("test-password".toCharArray(), UnlockMode.PASSWORD_ONLY))
                assertTrue(viewModel.isInitialized.value)
                assertTrue(viewModel.isUnlocked.value)
                viewModel.addEntry(VaultEntry(title = "Example", username = "user", password = "secret"))
                val entry = withTimeout(10_000) { viewModel.allEntries.first { it.isNotEmpty() } }.single()
                assertEquals("secret", entry.password)
                viewModel.lockVault()
                assertFalse(viewModel.isUnlocked.value)
                assertTrue(viewModel.allEntries.value.isEmpty())
                assertNull(viewModel.getVaultKey())
                assertFalse(viewModel.unlockWithMasterPassword("wrong-password".toCharArray()))
                assertTrue(viewModel.unlockWithMasterPassword("test-password".toCharArray()))
                val restored = withTimeout(10_000) { viewModel.allEntries.first { it.isNotEmpty() } }.single()
                assertEquals(entry, restored)
            }
        } finally {
            viewModel.lockVault()
            Dispatchers.resetMain()
        }
    }
    @Test
    fun `fingerprint only vault can unlock and requires a password before disabling biometrics`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = VaultViewModel(ApplicationProvider.getApplicationContext<Application>())
        try {
            runBlocking {
                withTimeout(10_000) { viewModel.isLoading.first { !it } }
                assertFalse(viewModel.setupVault(charArrayOf(), UnlockMode.PASSWORD_ONLY))
                assertFalse(viewModel.setupVault(charArrayOf(), UnlockMode.FINGERPRINT_ONLY))
                // A software cipher stands in for the cipher authorized by device fingerprint hardware.
                val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 7 }, "AES")
                val encrypt = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                encrypt.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
                assertTrue(viewModel.setupVault(charArrayOf(), UnlockMode.FINGERPRINT_ONLY, encrypt))
                assertTrue(viewModel.isBiometricEnabled.value)
                assertTrue(viewModel.getMeta()!!.encryptedVaultKeyMaster.isEmpty())
                assertFalse(viewModel.setUnlockMode(UnlockMode.PASSWORD_ONLY, charArrayOf()))
                viewModel.lockVault()
                assertFalse(viewModel.unlockWithMasterPassword(charArrayOf()))
                val meta = viewModel.getMeta()!!
                val decrypt = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                decrypt.init(javax.crypto.Cipher.DECRYPT_MODE, key,
                    javax.crypto.spec.GCMParameterSpec(128, meta.biometricKeyIv))
                assertTrue(viewModel.unlockWithBiometric(decrypt))
                assertTrue(viewModel.setUnlockMode(UnlockMode.PASSWORD_ONLY, "new-password".toCharArray()))
                assertNull(viewModel.getMeta()!!.encryptedVaultKeyBiometric)
                viewModel.lockVault()
                assertTrue(viewModel.unlockWithMasterPassword("new-password".toCharArray()))
            }
        } finally {
            viewModel.lockVault()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `combined mode requires both factors and preserves entries through mode and password changes`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = VaultViewModel(ApplicationProvider.getApplicationContext<Application>())
        val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 9 }, "AES")
        fun encryptionCipher() = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        }
        suspend fun decryptionCipher() = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.DECRYPT_MODE, key,
                javax.crypto.spec.GCMParameterSpec(128, viewModel.getMeta()!!.biometricKeyIv))
        }
        try {
            runBlocking {
                withTimeout(10_000) { viewModel.isLoading.first { !it } }
                assertFalse(viewModel.setupVault(charArrayOf(), UnlockMode.PASSWORD_AND_FINGERPRINT, encryptionCipher()))
                assertFalse(viewModel.setupVault("password".toCharArray(), UnlockMode.PASSWORD_AND_FINGERPRINT))
                assertTrue(viewModel.setupVault("password".toCharArray(), UnlockMode.PASSWORD_AND_FINGERPRINT, encryptionCipher()))
                viewModel.addEntry(VaultEntry(title = "Combined", username = "user", password = "secret"))
                val entry = withTimeout(10_000) { viewModel.allEntries.first { it.isNotEmpty() } }.single()
                viewModel.lockVault()
                assertFalse(viewModel.unlockWithMasterPassword("password".toCharArray()))
                assertFalse(viewModel.unlockWithBiometric(decryptionCipher()))
                assertFalse(viewModel.unlockWithBiometric(decryptionCipher(), "wrong".toCharArray()))
                assertFalse(viewModel.isUnlocked.value)
                assertNull(viewModel.getVaultKey())
                assertTrue(viewModel.unlockWithBiometric(decryptionCipher(), "password".toCharArray()))
                assertEquals(entry, withTimeout(10_000) { viewModel.allEntries.first { it.isNotEmpty() } }.single())
                assertTrue(viewModel.changeMasterPassword("password".toCharArray(), "changed-password".toCharArray()))
                viewModel.lockVault()
                assertFalse(viewModel.unlockWithBiometric(decryptionCipher(), "password".toCharArray()))
                assertTrue(viewModel.unlockWithBiometric(decryptionCipher(), "changed-password".toCharArray()))
                assertTrue(viewModel.setUnlockMode(UnlockMode.FINGERPRINT_ONLY, charArrayOf(), encryptionCipher()))
                viewModel.lockVault()
                assertFalse(viewModel.unlockWithMasterPassword("changed-password".toCharArray()))
                assertTrue(viewModel.unlockWithBiometric(decryptionCipher()))
                assertEquals(entry, withTimeout(10_000) { viewModel.allEntries.first { it.isNotEmpty() } }.single())
            }
        } finally {
            viewModel.lockVault()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `confirmed deletion works without fresh credentials in each protection mode`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val application = ApplicationProvider.getApplicationContext<Application>()
        val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 11 }, "AES")
        try {
            runBlocking {
                for (mode in UnlockMode.entries) {
                    val dao = com.example.data.VaultDatabase.getDatabase(application).vaultDao()
                    dao.deleteAllEntries()
                    dao.deleteMeta()
                    val viewModel = VaultViewModel(application)
                    try {
                        withTimeout(10_000) { viewModel.isLoading.first { !it } }
                        val encrypt = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(javax.crypto.Cipher.ENCRYPT_MODE, key)
                        }
                        val password = if (mode.requiresPassword) "password".toCharArray() else charArrayOf()
                        assertTrue(viewModel.setupVault(password, mode, if (mode.requiresFingerprint) encrypt else null))
                        viewModel.addEntry(VaultEntry(title = "Delete me", username = "user", password = "secret"))
                        val entry = withTimeout(10_000) { viewModel.allEntries.first { it.isNotEmpty() } }.single()
                        assertEquals(entry, viewModel.allEntries.value.single())
                        assertTrue(viewModel.deleteEntry(entry.id))
                        withTimeout(10_000) { viewModel.allEntries.first { it.isEmpty() } }
                        viewModel.lockVault()
                        assertFalse(viewModel.deleteEntry(entry.id))
                    } finally { viewModel.lockVault() }
                }
            }
        } finally { Dispatchers.resetMain() }
    }

}
