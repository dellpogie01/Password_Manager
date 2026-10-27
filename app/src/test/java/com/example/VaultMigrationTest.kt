package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.VaultDatabase
import com.example.model.UnlockMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultMigrationTest {
    @Test
    fun `version one vault migrates without losing encrypted credentials`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        old.execSQL("CREATE TABLE vault_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, titleCipher BLOB NOT NULL, titleIv BLOB NOT NULL, usernameCipher BLOB NOT NULL, usernameIv BLOB NOT NULL, passwordCipher BLOB NOT NULL, passwordIv BLOB NOT NULL, urlCipher BLOB NOT NULL, urlIv BLOB NOT NULL, category TEXT NOT NULL, notesCipher BLOB NOT NULL, notesIv BLOB NOT NULL, isFavorite INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        old.execSQL("CREATE TABLE vault_meta (id INTEGER NOT NULL PRIMARY KEY, salt BLOB NOT NULL, kdfIterations INTEGER NOT NULL, encryptedVaultKeyMaster BLOB NOT NULL, masterKeyIv BLOB NOT NULL, encryptedVaultKeyBiometric BLOB, biometricKeyIv BLOB, isBiometricEnabled INTEGER NOT NULL)")
        old.execSQL("INSERT INTO vault_meta VALUES (1, X'01', 100000, X'0203', X'04', NULL, NULL, 1)")
        old.version = 1
        old.close()
        val migrated = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(VaultDatabase.MIGRATION_1_2).build()
        try {
            val meta = migrated.vaultDao().getMeta()!!
            assertArrayEquals(byteArrayOf(2, 3), meta.encryptedVaultKeyMaster)
            assertEquals("LEGACY", meta.unlockMode)
            // Early versions could mark biometrics enabled without storing a biometric key.
            assertEquals(UnlockMode.PASSWORD_ONLY, UnlockMode.fromMeta(meta))
            assertEquals(UnlockMode.FINGERPRINT_ONLY, UnlockMode.fromMeta(meta.copy(
                encryptedVaultKeyBiometric = byteArrayOf(5), biometricKeyIv = byteArrayOf(6))))
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
