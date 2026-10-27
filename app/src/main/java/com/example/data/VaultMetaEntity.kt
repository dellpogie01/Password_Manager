package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_meta")
data class VaultMetaEntity(
    @PrimaryKey val id: Int = 1,
    val salt: ByteArray,
    val kdfIterations: Int = 100000,
    val encryptedVaultKeyMaster: ByteArray,
    val masterKeyIv: ByteArray,
    val encryptedVaultKeyBiometric: ByteArray?,
    val biometricKeyIv: ByteArray?,
    val isBiometricEnabled: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "'LEGACY'")
    val unlockMode: String = "LEGACY"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultMetaEntity
        if (id != other.id) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!encryptedVaultKeyMaster.contentEquals(other.encryptedVaultKeyMaster)) return false
        return true
    }

    override fun hashCode(): Int {
        return id
    }
}
